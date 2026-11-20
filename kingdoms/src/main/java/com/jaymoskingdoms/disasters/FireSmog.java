package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.JaymosKingdoms;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Where the big fires are, for the smog over them (Andy, 2026-11-20: "it should go for any large flame so players can see
 * it from a distance"). Every fire block's own tick ({@code FireBlockSmogMixin}) is counted into 16x16 cells; every
 * couple of seconds each player is sent the cells round them that hold a large flame (a burning house, a burning tree, a
 * forest), out to {@link #SEND_RANGE} blocks, and the client hangs dark smog over each ({@code FireSmoke}). A fire in a
 * loaded chunk too far off to tick still burns, so its cell is kept as it was until the chunk ticks or unloads.
 * Not in the Nether.
 */
public final class FireSmog {
	/** Cells are 2^CELL blocks across. */
	public static final int CELL = 4;
	/** How often cells are counted and sent (ticks); fire ticks every 30 to 40, so each fire counts about once a round. */
	static final int EVERY = 40;
	/** A cell is sent once it holds about this many fires; one under ONE fire is forgotten. */
	public static final float FROM = 8.0F;
	static final float ONE = 1.0F;
	/** How much of last round's count a cell keeps (it steadies the count; a fire gone out fades in a few rounds). */
	static final float KEEP = 0.5F;
	/** How far round a player cells are sent (blocks), and the most sent (the nearest). */
	static final int SEND_RANGE = 512, MAX_SENT = 64;

	private static final class Cell {
		int count, topNow = Integer.MIN_VALUE, top;
		float fires;
	}

	private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Cell>> LEVELS = new HashMap<>();

	private FireSmog() {
	}

	/** One big fire's cell: its middle, the height of its highest flame, and about how many fires burn in it. */
	public record Source(int x, int z, int top, float fires) {
	}

	/** Server → client: the large flames round the player. */
	public record Sync(List<Source> sources) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosKingdoms.id("fire_smog"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.sources.size());
				for (Source s : p.sources) {
					buf.writeInt(s.x);
					buf.writeInt(s.z);
					buf.writeShort(s.top);
					buf.writeFloat(s.fires);
				}
			},
			buf -> {
				int n = Math.min(buf.readVarInt(), MAX_SENT);
				List<Source> sources = new ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					sources.add(new Source(buf.readInt(), buf.readInt(), buf.readShort(), buf.readFloat()));
				}
				return new Sync(sources);
			});

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
	}

	static void clear() {
		LEVELS.clear();
	}

	/** (FireBlockSmogMixin) A fire block at {@code pos} ticked. */
	public static void burning(ServerLevel level, BlockPos pos) {
		if (level.dimension() == Level.NETHER) {
			return;
		}
		Cell cell = LEVELS.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>())
			.computeIfAbsent(BlockPos.asLong(pos.getX() >> CELL, 0, pos.getZ() >> CELL), k -> new Cell());
		cell.count++;
		cell.topNow = Math.max(cell.topNow, pos.getY());
	}

	static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Long2ObjectOpenHashMap<Cell> cells = LEVELS.get(level.dimension());
			if (cells != null) {
				count(level, cells);
				if (cells.isEmpty()) {
					LEVELS.remove(level.dimension());
					cells = null;
				}
			}
			// (an empty list too, so a player's client lets go of fires that are out, or in another world)
			for (ServerPlayer player : level.players()) {
				ServerPlayNetworking.send(player, new Sync(cells == null ? List.of() : near(cells, player)));
			}
		}
	}

	/** Each cell takes this round's count; the cold ones are forgotten, the frozen ones kept. */
	private static void count(ServerLevel level, Long2ObjectOpenHashMap<Cell> cells) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (Iterator<Long2ObjectMap.Entry<Cell>> it = cells.long2ObjectEntrySet().fastIterator(); it.hasNext(); ) {
			Long2ObjectMap.Entry<Cell> e = it.next();
			Cell cell = e.getValue();
			at.set((BlockPos.getX(e.getLongKey()) << CELL) + 8, cell.top, (BlockPos.getZ(e.getLongKey()) << CELL) + 8);
			if (!level.isLoaded(at)) {
				it.remove();
				continue;
			}
			if (cell.count > 0) {
				cell.fires = cell.fires <= 0.0F ? cell.count : cell.fires * KEEP + cell.count * (1.0F - KEEP);
				cell.top = cell.topNow;
			} else if (level.shouldTickBlocksAt(at)) {
				cell.fires *= KEEP;
			}
			cell.count = 0;
			cell.topNow = Integer.MIN_VALUE;
			if (cell.fires < ONE) {
				it.remove();
			}
		}
	}

	/** The big cells within SEND_RANGE of the player, nearest first, at most MAX_SENT. */
	private static List<Source> near(Long2ObjectOpenHashMap<Cell> cells, ServerPlayer player) {
		List<Source> out = new ArrayList<>();
		double range2 = (double)SEND_RANGE * SEND_RANGE;
		for (Long2ObjectMap.Entry<Cell> e : cells.long2ObjectEntrySet()) {
			Cell cell = e.getValue();
			if (cell.fires < FROM) {
				continue;
			}
			int x = (BlockPos.getX(e.getLongKey()) << CELL) + 8, z = (BlockPos.getZ(e.getLongKey()) << CELL) + 8;
			double dx = x - player.getX(), dz = z - player.getZ();
			if (dx * dx + dz * dz <= range2) {
				out.add(new Source(x, z, cell.top, cell.fires));
			}
		}
		if (out.size() > MAX_SENT) {
			out.sort((a, b) -> Double.compare(dist2(a, player), dist2(b, player)));
			out = new ArrayList<>(out.subList(0, MAX_SENT));
		}
		return out;
	}

	private static double dist2(Source s, ServerPlayer player) {
		double dx = s.x - player.getX(), dz = s.z - player.getZ();
		return dx * dx + dz * dz;
	}
}
