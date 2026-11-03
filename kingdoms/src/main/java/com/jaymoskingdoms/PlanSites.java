package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.488) Laid-out plans you build by hand, as a ship's build kit is: a {@link MasonTable building plan} used on the
 * ground leaves its building standing there as a see-through ghost of every block (drawn on the client by
 * {@code PlanGhosts}), seen by everyone near it. Put the right blocks where the ghosts are, by hand, with whatever you
 * like (a block counts whichever way it's turned; dirt does for grass and paths), and the ghosts go as they're filled.
 * Standing in it shows how far along it is. Once {@link #QUOTA} of its blocks are in it's built: the ghosts go, and
 * whoever laid it is told. A mason's table given the same plan builds into the same ghosts.
 * <p>
 * A plan stands in one place at a time (laying it again moves it); one player keeps at most {@link #MAX_PER_PLAYER}.
 */
public final class PlanSites {
	/** The share of a plan's blocks that must be in for it to count as built. */
	static final double QUOTA = 0.9;
	/** The most laid plans one player keeps standing (the oldest goes). */
	static final int MAX_PER_PLAYER = 3;
	/** How far away a laid plan's ghosts are sent to a player (blocks); how often that's looked at (ticks). */
	static final int SEND_RANGE = 128, SEND_TICKS = 20;
	/** Blocks looked at per tick, over every plan, to count what's in. */
	static final int SCAN_PER_TICK = 4096;
	/** How near (blocks, beyond its edges) a player must be to be shown its progress. */
	static final int PROGRESS_REACH = 4;
	/** The most blocks in one ghost packet. */
	static final int CELLS_PER_PACKET = 16000;

	/** A laid plan: its id (on the plan item too), design, how turned, its lowest north-west corner, where, and whose. */
	public record Site(UUID id, String design, int rotation, BlockPos anchor, String dimension, UUID owner, String ownerName, long laidAt) {
		public static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("id").forGetter(Site::id),
				Codec.STRING.fieldOf("design").forGetter(Site::design),
				Codec.INT.optionalFieldOf("rot", 0).forGetter(Site::rotation),
				BlockPos.CODEC.fieldOf("anchor").forGetter(Site::anchor),
				Codec.STRING.fieldOf("dim").forGetter(Site::dimension),
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Site::owner),
				Codec.STRING.optionalFieldOf("owner_name", "").forGetter(Site::ownerName),
				Codec.LONG.optionalFieldOf("laid_at", 0L).forGetter(Site::laidAt))
			.apply(i, Site::new));

		Rotation turn() {
			return Rotation.values()[Math.floorMod(this.rotation, 4)];
		}
	}

	/** Every laid plan, by id. Kept on the Overworld. */
	static final AttachmentType<Map<String, Site>> SITES = AttachmentRegistry.create(JaymosKingdoms.id("plan_sites"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Site.CODEC)));

	/** A site's blocks worked out from its design: places (absolute), states, and which count toward the quota. */
	private static final class Cells {
		final Site site;
		final long[] pos;
		final BlockState[] states;
		final boolean[] counts;
		final int countable;
		/** The counting pass: where it's got to, what it's found so far, and the last full pass's total (-1: none yet). */
		int cursor, found, matched = -1;

		Cells(Site site, long[] pos, BlockState[] states, boolean[] counts) {
			this.site = site;
			this.pos = pos;
			this.states = states;
			this.counts = counts;
			int n = 0;
			for (boolean c : counts) {
				if (c) {
					n++;
				}
			}
			this.countable = n;
		}

		int quota() {
			return Math.max(1, (int)Math.ceil(this.countable * QUOTA));
		}
	}

	private static final Map<UUID, Cells> CELLS = new HashMap<>();
	/** What each player's been sent: site id → the site as sent. */
	private static final Map<UUID, Map<UUID, Site>> SENT = new HashMap<>();

	/** Server to client: (part of) a laid plan's ghosts. {@code rel}: x | y << 8 | z << 16 from the anchor; {@code states}: block state ids. */
	public record Ghost(UUID id, BlockPos anchor, int part, int parts, int[] rel, int[] states) implements CustomPacketPayload {
		public static final Type<Ghost> TYPE = new Type<>(JaymosKingdoms.id("plan_ghost"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ghost> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeUUID(p.id);
			buf.writeBlockPos(p.anchor);
			buf.writeVarInt(p.part);
			buf.writeVarInt(p.parts);
			buf.writeVarInt(p.rel.length);
			for (int i = 0; i < p.rel.length; i++) {
				buf.writeVarInt(p.rel[i]);
				buf.writeVarInt(p.states[i]);
			}
		}, PlanSites::readGhost);

		@Override
		public Type<Ghost> type() {
			return TYPE;
		}
	}

	private static Ghost readGhost(FriendlyByteBuf buf) {
		UUID id = buf.readUUID();
		BlockPos anchor = buf.readBlockPos();
		int part = buf.readVarInt(), parts = buf.readVarInt();
		int n = Math.min(buf.readVarInt(), CELLS_PER_PACKET);
		int[] rel = new int[n], states = new int[n];
		for (int i = 0; i < n; i++) {
			rel[i] = buf.readVarInt();
			states[i] = buf.readVarInt();
		}
		return new Ghost(id, anchor, part, parts, rel, states);
	}

	/** Server to client: a laid plan's ghosts are gone (built, moved, or out of range). */
	public record Gone(UUID id) implements CustomPacketPayload {
		public static final Type<Gone> TYPE = new Type<>(JaymosKingdoms.id("plan_ghost_gone"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Gone> CODEC = CustomPacketPayload.codec((p, buf) -> buf.writeUUID(p.id),
			buf -> new Gone(buf.readUUID()));

		@Override
		public Type<Gone> type() {
			return TYPE;
		}
	}

	private PlanSites() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Ghost.TYPE, Ghost.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Gone.TYPE, Gone.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(PlanSites::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SENT.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CELLS.clear();
			SENT.clear();
		});
	}

	static Map<String, Site> all(MinecraftServer server) {
		return server.overworld().getAttachedOrElse(SITES, Map.<String, Site>of());
	}

	private static void put(MinecraftServer server, Map<String, Site> sites) {
		server.overworld().setAttached(SITES, Map.copyOf(sites));
	}

	static @Nullable Site get(MinecraftServer server, @Nullable UUID id) {
		return id == null ? null : all(server).get(id.toString());
	}

	/**
	 * Lays (or moves, or turns) plan {@code id}'s building: design {@code design} turned {@code rotation} with its corner
	 * at {@code anchor} where {@code player} is. Their oldest goes if they've too many standing.
	 */
	static void lay(ServerPlayer player, UUID id, String design, Rotation rotation, BlockPos anchor) {
		MinecraftServer server = player.level().getServer();
		Map<String, Site> sites = new HashMap<>(all(server));
		Site old = sites.get(id.toString());
		sites.put(id.toString(), new Site(id, design, rotation.ordinal(), anchor.immutable(), player.level().dimension().identifier().toString(), player.getUUID(),
			player.getGameProfile().name(), old != null ? old.laidAt() : server.overworld().getGameTime()));
		List<Site> mine = new ArrayList<>(sites.values().stream().filter(s -> s.owner().equals(player.getUUID())).toList());
		mine.sort((a, b) -> Long.compare(a.laidAt(), b.laidAt()));
		while (mine.size() > MAX_PER_PLAYER) {
			Site gone = mine.remove(0);
			if (!gone.id().equals(id)) {
				sites.remove(gone.id().toString());
				CELLS.remove(gone.id());
			}
		}
		put(server, sites);
		CELLS.remove(id);
	}

	/** Takes plan {@code id}'s building down (a new design was drawn on the plan). */
	static void remove(MinecraftServer server, @Nullable UUID id) {
		if (id == null || !all(server).containsKey(id.toString())) {
			return;
		}
		Map<String, Site> sites = new HashMap<>(all(server));
		sites.remove(id.toString());
		put(server, sites);
		CELLS.remove(id);
	}

	/** {@code site}'s blocks, worked out from its design (cached); null if the design's gone. */
	private static @Nullable Cells cells(MinecraftServer server, Site site) {
		Cells c = CELLS.get(site.id());
		if (c != null && c.site.equals(site)) {
			return c;
		}
		Blueprint bp = Designs.blueprint(server, site.design());
		if (bp == null) {
			return null;
		}
		List<Blueprint.Placement> blocks = bp.blocks();
		long[] pos = new long[blocks.size()];
		BlockState[] states = new BlockState[blocks.size()];
		boolean[] counts = new boolean[blocks.size()];
		int n = 0;
		for (Blueprint.Placement pl : blocks) {
			BlockState state = pl.state().rotate(site.turn());
			if (state.isAir() || MasonTable.cost(state) == null) {
				// (water, fire, portals: nothing to put there by hand)
				continue;
			}
			if (state.hasProperty(BlockStateProperties.WATERLOGGED)) {
				state = state.setValue(BlockStateProperties.WATERLOGGED, false);
			}
			pos[n] = site.anchor().offset(Planner.turn(bp, pl.offset(), site.turn())).asLong();
			states[n] = state;
			counts[n] = !MasonTable.secondHalf(state);
			n++;
		}
		c = new Cells(site, java.util.Arrays.copyOf(pos, n), java.util.Arrays.copyOf(states, n), java.util.Arrays.copyOf(counts, n));
		CELLS.put(site.id(), c);
		return c;
	}

	/** Whether {@code here} does for {@code want}: the same block, any way turned; dirt and its kin for one another. */
	public static boolean matches(BlockState here, BlockState want) {
		if (here.is(want.getBlock())) {
			return true;
		}
		return soil(want) && soil(here);
	}

	private static boolean soil(BlockState s) {
		Item item = s.getBlock().asItem();
		return s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT) || MasonTable.SOIL.contains(item);
	}

	private static void tick(MinecraftServer server) {
		Map<String, Site> sites = all(server);
		if (sites.isEmpty() && SENT.isEmpty()) {
			return;
		}
		count(server, sites);
		if (server.getTickCount() % SEND_TICKS == 0) {
			send(server, all(server));
		}
	}

	/** Counts what's in, a slice a tick; a plan that's reached its quota is built. */
	private static void count(MinecraftServer server, Map<String, Site> sites) {
		// one plan a tick, in turn
		List<Site> list = List.copyOf(sites.values());
		if (list.isEmpty()) {
			return;
		}
		int budget = SCAN_PER_TICK;
		List<Site> built = new ArrayList<>();
		for (Site site : List.of(list.get(server.getTickCount() % list.size()))) {
			ServerLevel level = level(server, site.dimension());
			Cells c = level == null ? null : cells(server, site);
			if (c == null || c.pos.length == 0) {
				continue;
			}
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			while (budget > 0) {
				budget--;
				if (c.cursor >= c.pos.length) {
					c.matched = c.found;
					c.cursor = 0;
					c.found = 0;
					if (c.matched >= c.quota()) {
						built.add(site);
					}
					break;
				}
				int i = c.cursor++;
				if (!c.counts[i]) {
					continue;
				}
				at.set(c.pos[i]);
				if (!level.isLoaded(at)) {
					// (not all of it loaded: start again later)
					c.cursor = 0;
					c.found = 0;
					break;
				}
				if (matches(level.getBlockState(at), c.states[i])) {
					c.found++;
				}
			}
		}
		for (Site site : built) {
			finish(server, site);
		}
	}

	private static void finish(MinecraftServer server, Site site) {
		Cells c = CELLS.get(site.id());
		remove(server, site.id());
		ServerLevel level = level(server, site.dimension());
		if (level == null) {
			return;
		}
		level.playSound(null, site.anchor(), SoundEvents.VILLAGER_WORK_MASON, SoundSource.BLOCKS, 1.2F, 1.0F);
		level.playSound(null, site.anchor(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7F, 0.8F);
		Component msg = Component.translatable("message.jaymoskingdoms.plan_site.built", site.design(), c == null ? 0 : c.matched, c == null ? 0 : c.countable)
			.withStyle(ChatFormatting.GOLD);
		ServerPlayer owner = server.getPlayerList().getPlayer(site.owner());
		if (owner != null) {
			owner.sendSystemMessage(msg);
		}
		for (ServerPlayer p : level.players()) {
			if (p != owner && near(site, c, p, PROGRESS_REACH)) {
				p.sendSystemMessage(msg);
			}
		}
		JaymosKingdoms.LOGGER.info("{}'s laid plan {} is built", site.ownerName(), site.design());
	}

	private static @Nullable ServerLevel level(MinecraftServer server, String dimension) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension().identifier().toString().equals(dimension)) {
				return level;
			}
		}
		return null;
	}

	/** Whether {@code player} stands within {@code reach} of {@code site}'s box. */
	private static boolean near(Site site, @Nullable Cells c, ServerPlayer player, int reach) {
		if (c == null || c.pos.length == 0) {
			return false;
		}
		BlockPos p = player.blockPosition();
		int[] box = box(c);
		return p.getX() >= box[0] - reach && p.getX() <= box[3] + reach && p.getY() >= box[1] - reach && p.getY() <= box[4] + reach
			&& p.getZ() >= box[2] - reach && p.getZ() <= box[5] + reach;
	}

	private static int[] box(Cells c) {
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (long l : c.pos) {
			int x = BlockPos.getX(l), y = BlockPos.getY(l), z = BlockPos.getZ(l);
			b[0] = Math.min(b[0], x);
			b[1] = Math.min(b[1], y);
			b[2] = Math.min(b[2], z);
			b[3] = Math.max(b[3], x);
			b[4] = Math.max(b[4], y);
			b[5] = Math.max(b[5], z);
		}
		return b;
	}

	/** Sends each player the ghosts of laid plans near them (and takes back ones gone), and shows progress to those inside one. */
	private static void send(MinecraftServer server, Map<String, Site> sites) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Map<UUID, Site> sent = SENT.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
			String dim = player.level().dimension().identifier().toString();
			Set<UUID> keep = new HashSet<>();
			for (Site site : sites.values()) {
				if (!site.dimension().equals(dim) || site.anchor().distSqr(player.blockPosition()) > (double)SEND_RANGE * SEND_RANGE) {
					continue;
				}
				Cells c = cells(server, site);
				if (c == null) {
					continue;
				}
				keep.add(site.id());
				if (!site.equals(sent.get(site.id()))) {
					sendGhost(player, c);
					sent.put(site.id(), site);
				}
				if (c.matched >= 0 && near(site, c, player, PROGRESS_REACH)) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.plan_site.progress", site.design(), c.matched, c.quota(), c.countable)
						.withStyle(ChatFormatting.YELLOW));
				}
			}
			for (UUID id : new ArrayList<>(sent.keySet())) {
				if (!keep.contains(id)) {
					sent.remove(id);
					ServerPlayNetworking.send(player, new Gone(id));
				}
			}
		}
	}

	private static void sendGhost(ServerPlayer player, Cells c) {
		BlockPos anchor = c.site.anchor();
		int n = c.pos.length;
		int parts = Math.max(1, (n + CELLS_PER_PACKET - 1) / CELLS_PER_PACKET);
		for (int part = 0; part < parts; part++) {
			int from = part * CELLS_PER_PACKET, to = Math.min(n, from + CELLS_PER_PACKET);
			int[] rel = new int[to - from], states = new int[to - from];
			for (int i = from; i < to; i++) {
				long l = c.pos[i];
				rel[i - from] = (BlockPos.getX(l) - anchor.getX()) & 0xFF | ((BlockPos.getY(l) - anchor.getY()) & 0xFF) << 8 | ((BlockPos.getZ(l) - anchor.getZ()) & 0xFF) << 16;
				states[i - from] = Block.getId(c.states[i]);
			}
			ServerPlayNetworking.send(player, new Ghost(c.site.id(), anchor, part, parts, rel, states));
		}
	}
}
