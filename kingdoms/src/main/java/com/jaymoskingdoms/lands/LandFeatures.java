package com.jaymoskingdoms.lands;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.disasters.Disasters;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * What the new lands put on the ground: terraces of hot pools ({@link SpringTerrace}), lava vents ({@link LavaVent}) and
 * drifts of ash ({@link AshDrift}). Each is a placed feature in {@code data/jaymoskingdoms/worldgen}, listed by its biome.
 */
public final class LandFeatures {
	// ---- the terraces ----
	/** Pools in a terrace, at least and up to this many more; a pool's radius, at least and up to this many more. */
	static final int POOLS_MIN = 2, POOLS_EXTRA = 1, RADIUS_MIN = 2, RADIUS_EXTRA = 1;
	/** Layers of water in a pool (the top level with its terrace); the magma lies one under the gravel floor, as in Powers' pools. */
	static final int WATER_DEPTH = 2;
	/** How far the ground round a pool may rise above its water and drop below it, and how far a lower pool may lie under the one above. */
	static final int RISE_MAX = 4, DROP_MAX = 4, STEP_MAX = 3;
	/** A terrace stays this near its origin (a feature may only write one chunk round its own). */
	static final int REACH = 15;
	/** Of the lip blocks, this many in 100 are steaming calcite; of the low lip's, this many are raised a block. */
	static final int STEAMING = 30, LIP_RAISED = 30;
	/** How far away the ground is sampled to find which way is down. */
	static final int DOWNHILL = 7;
	// ---- the vents ----
	/** A vent's ring of smoldering basalt, its outer ring's chance (1 in n) of smoldering (else basalt or magma), and its chimney's chance and height. */
	static final int OUTER_SMOLDERS_ONE_IN = 2, CHIMNEY_ONE_IN = 2, CHIMNEY_MIN = 2, CHIMNEY_EXTRA = 2, VENT_FLAT = 1;
	// ---- the ash ----
	static final int ASH_RADIUS = 7, ASH_CHANCE = 45;

	private LandFeatures() {
	}

	static void register() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("spring_terrace"), SpringTerrace.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("lava_vent"), LavaVent.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosKingdoms.id("ash_drift"), AshDrift.CODEC);
	}

	/** The y of the ground's top block at (x, z). */
	private static int ground(WorldGenLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
	}

	private static void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
		BlockPos pos = new BlockPos(x, y, z);
		if (!level.getBlockState(pos).is(Blocks.BEDROCK)) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}

	private static boolean natural(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(Blocks.SNOW_BLOCK)
			|| state.is(Blocks.CALCITE) || state.is(Blocks.PODZOL) || state.is(Blocks.COARSE_DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.TUFF)
			|| state.is(Blocks.BASALT) || state.is(Blocks.BLACKSTONE) || state.is(Blocks.SMOOTH_BASALT);
	}

	/**
	 * Where: the steam valley, a pool or two or three stepping down a slope, each a terrace of calcite holding hot water.
	 * A pool is {@link #WATER_DEPTH} of water over a gravel floor over a magma block, the way Powers' springs are, so a
	 * bather has magma within three blocks under their feet and is warmed ({@code BodyHeat.hotSpring}); the gravel keeps the
	 * magma from burning them. The lip is calcite, some of it steaming.
	 */
	public record SpringTerrace() implements Feature {
		public static final MapCodec<SpringTerrace> CODEC = MapCodec.unit(SpringTerrace::new);

		@Override
		public MapCodec<SpringTerrace> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			// down the slope: of eight ways, the lowest ground a little way off
			double angle = 0.0;
			int lowest = Integer.MAX_VALUE;
			for (int i = 0; i < 8; i++) {
				double a = i * Math.PI / 4.0;
				int g = ground(level, origin.getX() + (int)Math.round(Math.cos(a) * DOWNHILL), origin.getZ() + (int)Math.round(Math.sin(a) * DOWNHILL));
				if (g < lowest) {
					lowest = g;
					angle = a;
				}
			}
			int pools = POOLS_MIN + random.nextInt(POOLS_EXTRA + 1);
			int x = origin.getX(), z = origin.getZ(), prevTop = Integer.MAX_VALUE, prevRadius = 0, made = 0;
			for (int i = 0; i < pools; i++) {
				int radius = RADIUS_MIN + random.nextInt(RADIUS_EXTRA + 1);
				if (i > 0) {
					int step = prevRadius + radius + 1;
					x += (int)Math.round(Math.cos(angle) * step);
					z += (int)Math.round(Math.sin(angle) * step);
				}
				if (Math.hypot(x - origin.getX(), z - origin.getZ()) + radius + 1 > REACH) {
					break;
				}
				int top = ground(level, x, z);
				if (i > 0 && (top > prevTop || prevTop - top > STEP_MAX)) {
					break;
				}
				if (!pool(level, random, x, top, z, radius)) {
					break;
				}
				prevTop = top;
				prevRadius = radius;
				made++;
			}
			return made > 0;
		}

		/** One pool whose water's top is level with {@code top}, the ground at its middle. */
		private static boolean pool(WorldGenLevel level, RandomSource random, int cx, int top, int cz, int radius) {
			int pool2 = radius * radius + 1, rim2 = (radius + 1) * (radius + 1) + 1;
			for (int dx = -radius - 1; dx <= radius + 1; dx++) {
				for (int dz = -radius - 1; dz <= radius + 1; dz++) {
					int d = dx * dx + dz * dz;
					if (d > rim2) {
						continue;
					}
					int g = ground(level, cx + dx, cz + dz);
					BlockPos surface = new BlockPos(cx + dx, g, cz + dz);
					if (!level.getFluidState(surface).isEmpty() || !level.getFluidState(surface.above()).isEmpty() || g - top > RISE_MAX) {
						return false;
					}
					if (d <= pool2 ? g < top - 1 || !natural(level.getBlockState(new BlockPos(cx + dx, top - WATER_DEPTH - 1, cz + dz)))
						: top - g > DROP_MAX) {
						return false;
					}
				}
			}
			for (int dx = -radius - 1; dx <= radius + 1; dx++) {
				for (int dz = -radius - 1; dz <= radius + 1; dz++) {
					int d = dx * dx + dz * dz;
					if (d > rim2) {
						continue;
					}
					int x = cx + dx, z = cz + dz, g = ground(level, x, z);
					if (d <= pool2) {
						boolean inner = d <= (radius - 1) * (radius - 1) + 1;
						set(level, x, top - WATER_DEPTH - 1, z, (inner ? Blocks.MAGMA_BLOCK : Blocks.CALCITE).defaultBlockState());
						set(level, x, top - WATER_DEPTH, z, Blocks.GRAVEL.defaultBlockState());
						for (int y = top - WATER_DEPTH + 1; y <= top; y++) {
							set(level, x, y, z, Blocks.WATER.defaultBlockState());
						}
						for (int y = top + 1; y <= g + 3; y++) {
							set(level, x, y, z, Blocks.AIR.defaultBlockState());
						}
						continue;
					}
					if (g >= top) {
						// uphill: the ground itself is the wall; its top is calcite
						if (g == top) {
							set(level, x, g, z, lip(random));
						}
						continue;
					}
					// downhill: a terrace wall of calcite built up from the ground to the water's level, sometimes a block higher
					for (int y = g + 1; y <= top; y++) {
						set(level, x, y, z, y == top ? lip(random) : Blocks.CALCITE.defaultBlockState());
					}
					if (random.nextInt(100) < LIP_RAISED) {
						set(level, x, top + 1, z, Blocks.CALCITE.defaultBlockState());
					}
				}
			}
			return true;
		}

		private static BlockState lip(RandomSource random) {
			return (random.nextInt(100) < STEAMING ? LandBlocks.STEAMING_CALCITE : Blocks.CALCITE).defaultBlockState();
		}
	}

	/**
	 * Where: the ashlands, on flat ground. A little crater of smoldering basalt (hot underfoot, smoking) round two blocks of lava,
	 * over a magma block, with now and then a chimney of basalt rising from its rim.
	 */
	public record LavaVent() implements Feature {
		public static final MapCodec<LavaVent> CODEC = MapCodec.unit(LavaVent::new);

		@Override
		public MapCodec<LavaVent> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			int x = origin.getX(), z = origin.getZ(), y = ground(level, x, z);
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					BlockPos at = new BlockPos(x + dx, ground(level, x + dx, z + dz), z + dz);
					if (Math.abs(at.getY() - y) > VENT_FLAT || !level.getFluidState(at).isEmpty() || !level.getFluidState(at.above()).isEmpty()
						|| !natural(level.getBlockState(at)) && !level.getBlockState(at).is(com.jaymoskingdoms.TerrainFeatures.BLACK_SAND)) {
						return false;
					}
				}
			}
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					int d = dx * dx + dz * dz;
					if (d > 5) {
						continue;
					}
					int gx = x + dx, gz = z + dz;
					if (d == 0) {
						set(level, gx, y - 2, gz, Blocks.MAGMA_BLOCK.defaultBlockState());
						set(level, gx, y - 1, gz, Blocks.LAVA.defaultBlockState());
						set(level, gx, y, gz, Blocks.LAVA.defaultBlockState());
					} else if (d <= 2) {
						set(level, gx, y - 1, gz, Blocks.BASALT.defaultBlockState());
						set(level, gx, y, gz, LandBlocks.SMOLDERING_BASALT.defaultBlockState());
					} else {
						int roll = random.nextInt(OUTER_SMOLDERS_ONE_IN + 1);
						set(level, gx, y, gz, (roll < OUTER_SMOLDERS_ONE_IN - 1 ? LandBlocks.SMOLDERING_BASALT : roll == OUTER_SMOLDERS_ONE_IN - 1 ? Blocks.MAGMA_BLOCK
							: Blocks.BASALT).defaultBlockState());
					}
					for (int up = 1; up <= 3; up++) {
						set(level, gx, y + up, gz, Blocks.AIR.defaultBlockState());
					}
				}
			}
			set(level, x, y + 1, z, Blocks.AIR.defaultBlockState());
			if (random.nextInt(CHIMNEY_ONE_IN) == 0) {
				int side = random.nextBoolean() ? 1 : -1;
				int height = CHIMNEY_MIN + random.nextInt(CHIMNEY_EXTRA + 1);
				boolean alongX = random.nextBoolean();
				int cx = x + (alongX ? 2 * side : 0), cz = z + (alongX ? 0 : 2 * side);
				for (int up = 1; up <= height; up++) {
					set(level, cx, y + up, cz, Blocks.BASALT.defaultBlockState());
				}
			}
			return true;
		}
	}

	/** Where: the ashlands. A drift of ash, a layer or three deep, over the sand and stone round the origin. */
	public record AshDrift() implements Feature {
		public static final MapCodec<AshDrift> CODEC = MapCodec.unit(AshDrift::new);

		@Override
		public MapCodec<AshDrift> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			boolean any = false;
			for (int dx = -ASH_RADIUS; dx <= ASH_RADIUS; dx++) {
				for (int dz = -ASH_RADIUS; dz <= ASH_RADIUS; dz++) {
					if (dx * dx + dz * dz > ASH_RADIUS * ASH_RADIUS || random.nextInt(100) >= ASH_CHANCE) {
						continue;
					}
					int x = origin.getX() + dx, z = origin.getZ() + dz, g = ground(level, x, z);
					BlockPos floor = new BlockPos(x, g, z), at = floor.above();
					BlockState below = level.getBlockState(floor);
					if (level.getBlockState(at).isAir() && level.getFluidState(floor).isEmpty() && below.isFaceSturdy(level, floor, net.minecraft.core.Direction.UP)
						&& !below.is(Blocks.MAGMA_BLOCK) && !below.is(LandBlocks.SMOLDERING_BASALT) && !below.is(Blocks.LAVA)) {
						level.setBlock(at, Disasters.ASH_LAYER.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + random.nextInt(3)), Block.UPDATE_CLIENTS);
						any = true;
					}
				}
			}
			return any;
		}
	}
}
