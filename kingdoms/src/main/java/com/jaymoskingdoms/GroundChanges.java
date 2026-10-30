package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.381, Andy's SevTech picks: "The Ground Changes") Nobody needs fresh land for the next metal. When a player opens a
 * world tier, the chunks already loaded round them get that tier's veins, a few pockets of ore laid over deep stone: iron and
 * copper for Borderlands, gold and lapis for Wildlands, diamond for Dread and, in the Nether, ancient debris for Legend.
 * Each chunk remembers the highest tier it has been seeded for ({@link #SEEDED}, saved with the chunk) so a tier is
 * seeded once per chunk however many players pass through. It runs lazily: every {@link #SCAN_TICKS} ticks each player's
 * surroundings are walked a few chunks at a time ({@link #LOOKS_PER_WALK}), and at most {@link #SEEDS_PER_WALK} chunks are
 * seeded in a walk, so a tier opening never stalls a tick. A world tier floor raise ({@link WorldTiers#raiseFloor}) lifts
 * every player's tier, so it seeds the ground round everyone.
 */
public final class GroundChanges {
	// ---- tuning ----
	/** How often players' surroundings are walked (ticks), how many chunks are looked at for each player, and the most seeded in a walk. */
	static final int SCAN_TICKS = 4, LOOKS_PER_WALK = 12, SEEDS_PER_WALK = 2;
	/** How far round a player (in chunks) the walk reaches. */
	static final int RADIUS = 8;

	/**
	 * A kind of vein: the ore laid over stone and over deepslate (deepslate null: Nether stone), how many pockets a chunk gets,
	 * how many blocks a pocket is, the height range they sit in, and whether a block touching air is passed over (buried ore).
	 */
	record Vein(Block stone, @Nullable Block deep, int pockets, int size, int minY, int maxY, boolean buried) {
	}

	/** The Overworld's veins by tier (index 0 is Hearth's: none). */
	static final List<List<Vein>> OVERWORLD = List.of(
		List.of(),
		List.of(new Vein(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, 4, 7, -24, 56, false),
			new Vein(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, 3, 8, -16, 48, false)),
		List.of(new Vein(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, 3, 6, -48, 32, false),
			new Vein(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE, 2, 5, -32, 32, false)),
		List.of(new Vein(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, 2, 4, -60, 16, true)),
		List.of());
	/** The Nether's veins by tier. */
	static final List<List<Vein>> NETHER = List.of(
		List.of(), List.of(), List.of(), List.of(),
		List.of(new Vein(Blocks.ANCIENT_DEBRIS, null, 2, 3, 8, 118, true)));

	/** The highest tier a chunk has been seeded for. Saved with the chunk. */
	static final AttachmentType<Integer> SEEDED = AttachmentRegistry.create(JaymosKingdoms.id("ground_seeded"), builder -> builder.persistent(Codec.INT));

	/** Where each player's walk has got to (an index over the square of chunks round them). Not saved. */
	private static final Map<UUID, Integer> CURSOR = new HashMap<>();

	private GroundChanges() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("ground changes attachment: {}", SEEDED);
		ServerTickEvents.END_SERVER_TICK.register(GroundChanges::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CURSOR.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CURSOR.clear());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SCAN_TICKS != 0) {
			return;
		}
		int seeds = SEEDS_PER_WALK;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			int open = WorldTiers.progress(player).open();
			if (open <= 0 || player.isSpectator() || seeds <= 0) {
				continue;
			}
			ServerLevel level = player.level();
			List<List<Vein>> table = tableFor(level);
			if (table == null) {
				continue;
			}
			int side = 2 * RADIUS + 1, cells = side * side;
			int at = CURSOR.getOrDefault(player.getUUID(), 0);
			ChunkPos home = player.chunkPosition();
			for (int look = 0; look < LOOKS_PER_WALK && seeds > 0; look++) {
				at = (at + 1) % cells;
				LevelChunk chunk = level.getChunkSource().getChunkNow(home.x() + at % side - RADIUS, home.z() + at / side - RADIUS);
				if (chunk != null && seed(level, chunk, table, open)) {
					seeds--;
				}
			}
			CURSOR.put(player.getUUID(), at);
		}
	}

	private static @Nullable List<List<Vein>> tableFor(ServerLevel level) {
		return level.dimension() == Level.OVERWORLD ? OVERWORLD : level.dimension() == Level.NETHER ? NETHER : null;
	}

	/** Lays the veins of every tier up to {@code tier} that {@code chunk} hasn't had; whether it had any to lay. */
	static boolean seed(ServerLevel level, LevelChunk chunk, List<List<Vein>> table, int tier) {
		int done = chunk.getAttachedOrElse(SEEDED, 0);
		if (done >= tier) {
			return false;
		}
		ChunkPos pos = chunk.getPos();
		boolean laid = false;
		for (int t = done + 1; t <= tier && t < table.size(); t++) {
			RandomSource random = RandomSource.create(Mth.getSeed(pos.x(), t * 7919, pos.z()) ^ level.getSeed());
			for (Vein vein : table.get(t)) {
				for (int p = 0; p < vein.pockets(); p++) {
					laid |= pocket(level, chunk, vein, random);
				}
			}
		}
		chunk.setAttached(SEEDED, tier);
		chunk.markUnsaved();
		return laid;
	}

	/** One pocket: a short random walk from a point in the chunk, turning each replaceable stone block it meets into ore. */
	private static boolean pocket(ServerLevel level, LevelChunk chunk, Vein vein, RandomSource random) {
		int minY = Math.max(vein.minY(), level.getMinY() + 1), maxY = Math.min(vein.maxY(), level.getMaxY() - 2);
		if (maxY < minY) {
			return false;
		}
		ChunkPos cp = chunk.getPos();
		int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
		int x = x0 + random.nextInt(16), y = minY + random.nextInt(maxY - minY + 1), z = z0 + random.nextInt(16);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		boolean laid = false;
		for (int i = 0; i < vein.size(); i++) {
			pos.set(x, y, z);
			BlockState state = chunk.getBlockState(pos);
			Block ore = null;
			if (state.is(BlockTags.STONE_ORE_REPLACEABLES) || vein.deep() == null && state.is(BlockTags.BASE_STONE_NETHER)) {
				ore = vein.stone();
			} else if (state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
				ore = vein.deep();
			}
			if (ore != null && !(vein.buried() && exposed(chunk, pos))) {
				level.setBlock(pos, ore.defaultBlockState(), Block.UPDATE_CLIENTS);
				laid = true;
			}
			switch (random.nextInt(6)) {
				case 0 -> x = Mth.clamp(x + 1, x0, x0 + 15);
				case 1 -> x = Mth.clamp(x - 1, x0, x0 + 15);
				case 2 -> y = Mth.clamp(y + 1, minY, maxY);
				case 3 -> y = Mth.clamp(y - 1, minY, maxY);
				case 4 -> z = Mth.clamp(z + 1, z0, z0 + 15);
				default -> z = Mth.clamp(z - 1, z0, z0 + 15);
			}
		}
		return laid;
	}

	/** Whether a block of {@code chunk} touches air or a liquid (only the neighbours inside the chunk are looked at). */
	private static boolean exposed(LevelChunk chunk, BlockPos pos) {
		ChunkPos cp = chunk.getPos();
		for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
			BlockPos n = pos.relative(dir);
			if (n.getX() < cp.getMinBlockX() || n.getX() > cp.getMaxBlockX() || n.getZ() < cp.getMinBlockZ() || n.getZ() > cp.getMaxBlockZ()
				|| n.getY() < chunk.getMinY() || n.getY() > chunk.getMaxY()) {
				continue;
			}
			BlockState state = chunk.getBlockState(n);
			if (state.isAir() || !state.getFluidState().isEmpty()) {
				return true;
			}
		}
		return false;
	}
}
