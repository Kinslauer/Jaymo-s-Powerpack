package com.jaymospowers.alchemy.flux;

import com.jaymospowers.alchemy.Flux;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The life of a chunk's flux, run a little at a time: every {@link #VISIT_INTERVAL} ticks each player's surroundings
 * ({@link #RANGE} chunks) yield one random loaded chunk, which then (1) fades by {@link FluxEffects#FADE}, (2) gives part
 * of what is over {@link FluxEffects#SPREAD_AT} to its four neighbours, (3) over {@link FluxEffects#BLIGHT_AT} blights a few
 * random spots of its land and sickens its animals and (4) over {@link FluxEffects#WISP_AT}, at night, may loose a wisp.
 * Never every chunk every tick: a quiet world pays nearly nothing. A Blood Moon (Kingdoms, by reflection) doubles spreading
 * and wisp chances.
 */
final class FluxLife {
	// ---- tuning ----
	/** Ticks between one player's chunk visits, and how far (in chunks) around them a visit may fall. */
	public static final int VISIT_INTERVAL = 5, RANGE = 5;
	/** A neighbour gets (flux over the spread line) / this per visit, at least 1; doubled by a Blood Moon. */
	public static final int SPREAD_DIVISOR = 16;
	/** Spots tried per visit when blighting: this, plus one more per this much flux over the line (up to the cap). */
	public static final int BLIGHT_TRIES = 2, BLIGHT_STEP = 250, BLIGHT_MAX_TRIES = 6;
	/** A tried leaf withers one time in this many (leaves are slower, so soil gets its turn). */
	public static final int LEAF_ODDS = 3;
	/** A goo puddle appears on blighted ground one time in this many tries. */
	public static final int GOO_ODDS = 10;
	/** How long (ticks) the Blighted sickness is given for; renewed each visit while the chunk stays over the line. */
	public static final int SICK_TICKS = 1200;
	/** One visit in this many (over {@link FluxEffects#WISP_AT}, at night) looses a wisp; most wisps a chunk keeps. */
	public static final int WISP_ODDS = 8, WISP_CAP = 3;

	private FluxLife() {
	}

	static void tick(MinecraftServer server) {
		if (server.getTickCount() % VISIT_INTERVAL != 0) {
			return;
		}
		boolean blood = false, bloodKnown = false;
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			if (!bloodKnown) {
				blood = BloodMoon.active(server);
				bloodKnown = true;
			}
			RandomSource random = level.getRandom();
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator()) {
					continue;
				}
				ChunkPos at = player.chunkPosition();
				LevelChunk chunk = level.getChunkSource().getChunkNow(at.x() + random.nextInt(2 * RANGE + 1) - RANGE,
					at.z() + random.nextInt(2 * RANGE + 1) - RANGE);
				if (chunk != null) {
					visit(level, chunk, random, blood);
				}
			}
		}
	}

	private static int flux(LevelChunk chunk) {
		Integer n = chunk.getAttached(Flux.FLUX);
		return n == null ? 0 : n;
	}

	private static void setFlux(LevelChunk chunk, int value) {
		chunk.setAttached(Flux.FLUX, Math.max(0, Math.min(Flux.MAX, value)));
		chunk.markUnsaved();
	}

	private static void visit(ServerLevel level, LevelChunk chunk, RandomSource random, boolean blood) {
		int flux = flux(chunk);
		if (flux <= 0) {
			return;
		}
		// 1. fade
		flux = Math.max(0, flux - FluxEffects.FADE);
		// 2. spread to the four neighbours that are loaded
		if (flux > FluxEffects.SPREAD_AT) {
			int each = Math.max(1, (flux - FluxEffects.SPREAD_AT) / SPREAD_DIVISOR) * (blood ? 2 : 1);
			ChunkPos pos = chunk.getPos();
			int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] d : around) {
				LevelChunk next = level.getChunkSource().getChunkNow(pos.x() + d[0], pos.z() + d[1]);
				int there = next == null ? Integer.MAX_VALUE : flux(next);
				if (next != null && there + each < flux) {
					setFlux(next, there + each);
					flux -= each;
				}
			}
		}
		setFlux(chunk, flux);
		ChunkPos cp = chunk.getPos();
		// 3. the land and its animals
		if (flux >= FluxEffects.BLIGHT_AT) {
			int tries = Math.min(BLIGHT_MAX_TRIES, BLIGHT_TRIES + (flux - FluxEffects.BLIGHT_AT) / BLIGHT_STEP);
			for (int i = 0; i < tries; i++) {
				blightSpot(level, cp.getMinBlockX() + random.nextInt(16), cp.getMinBlockZ() + random.nextInt(16), random);
			}
			AABB box = new AABB(cp.getMinBlockX(), level.getMinY(), cp.getMinBlockZ(), cp.getMaxBlockX() + 1, level.getMaxY(), cp.getMaxBlockZ() + 1);
			for (Animal animal : level.getEntitiesOfClass(Animal.class, box, a -> !BlightedEffect.spared(a))) {
				MobEffectInstance sick = animal.getEffect(FluxEffects.BLIGHTED);
				if (sick == null || sick.getDuration() < SICK_TICKS / 2) {
					animal.addEffect(new MobEffectInstance(FluxEffects.BLIGHTED, SICK_TICKS, 0, false, true));
				}
			}
			// 4. wisps
			if (flux >= FluxEffects.WISP_AT && level.isDarkOutside() && random.nextInt(blood ? WISP_ODDS / 2 : WISP_ODDS) == 0
				&& level.getEntitiesOfClass(FluxWisp.class, box).size() < WISP_CAP) {
				spawnWisp(level, cp.getMinBlockX() + random.nextInt(16), cp.getMinBlockZ() + random.nextInt(16), random);
			}
		}
	}

	private static void spawnWisp(ServerLevel level, int x, int z, RandomSource random) {
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		FluxWisp wisp = FluxEffects.WISP.create(level, EntitySpawnReason.EVENT);
		if (wisp != null) {
			wisp.snapTo(x + 0.5, ground.getY() + 1.5 + random.nextDouble() * 2.0, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
			level.addFreshEntity(wisp);
		}
	}

	/** One try at blighting the surface at a column: a plant first, then leaves, then soil (and now and then a goo puddle). */
	private static void blightSpot(ServerLevel level, int x, int z, RandomSource random) {
		BlockPos above = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
		BlockPos top = above.below();
		BlockState plant = level.getBlockState(above);
		if (plant(plant)) {
			level.setBlockAndUpdate(above, FluxEffects.BLIGHT_GROWTH.defaultBlockState());
			return;
		}
		BlockState state = level.getBlockState(top);
		if (state.is(BlockTags.LEAVES)) {
			if (random.nextInt(LEAF_ODDS) == 0) {
				level.setBlockAndUpdate(top, FluxEffects.WITHERED_LEAVES.defaultBlockState());
			}
		} else if (FluxEffects.soil(state)) {
			level.setBlockAndUpdate(top, FluxEffects.BLIGHTED_SOIL.defaultBlockState());
		} else if (state.is(FluxEffects.BLIGHTED_SOIL) && plant.isAir() && random.nextInt(GOO_ODDS) == 0) {
			level.setBlockAndUpdate(above, FluxEffects.FLUX_GOO.defaultBlockState());
		}
	}

	/** A small plant (not a two-block one) that flux withers. */
	private static boolean plant(BlockState state) {
		if (state.isAir() || state.getBlock() instanceof DoublePlantBlock || state.is(FluxEffects.BLIGHT_GROWTH)) {
			return false;
		}
		Block block = state.getBlock();
		return state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(BlockTags.CROPS) || block == Blocks.SHORT_GRASS
			|| block == Blocks.FERN;
	}
}
