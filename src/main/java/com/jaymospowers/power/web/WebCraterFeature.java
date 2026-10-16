package com.jaymospowers.power.web;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A small meteor crater: a scorched bowl 7-11 blocks across lined with blackstone, coarse dirt, magma and obsidian,
 * with a lip of thrown-up debris and a ring of burnt ground, and the symbiote's goo sitting in the middle.
 *
 * <p>Placed rarely on the overworld surface (see {@code worldgen/placed_feature/web_crater.json}). It refuses to
 * generate on water, on steep ground, and anywhere its bowl would cut into anything that doesn't look like natural
 * ground (so it stays out of villages and other structures).
 */
public record WebCraterFeature() implements Feature {
	public static final MapCodec<WebCraterFeature> CODEC = MapCodec.unit(WebCraterFeature::new);

	/** Bowl radius range (7-11 blocks across), how deep the middle is, and the widths of the lip and scorch ring. */
	public static final double MIN_RADIUS = 3.5;
	public static final double MAX_RADIUS = 5.5;
	public static final int DEPTH = 3;
	public static final double RIM_WIDTH = 1.5;
	public static final double SCORCH_WIDTH = 2.5;
	/** The ground under the bowl and lip may vary this much from the centre's height, at most. */
	public static final int MAX_SLOPE = 3;
	/** How ragged the edges are (blocks). */
	private static final double EDGE_WOBBLE = 0.7;

	@Override
	public MapCodec<WebCraterFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		return carve(level, random, origin, true);
	}

	/**
	 * Makes a crater around {@code origin}'s column. {@code worldgen}: checks the site (and uses the world-generation
	 * heightmaps); otherwise (the debug command) it's carved where it's asked, into natural ground only.
	 */
	public static boolean carve(WorldGenLevel level, RandomSource random, BlockPos origin, boolean worldgen) {
		return carve(level, random, origin, worldgen, WebPower.GOO);
	}

	/** The same, with {@code goo} in the middle (the red symbiote's craters: {@link WebRedCraterFeature}). */
	public static boolean carve(WorldGenLevel level, RandomSource random, BlockPos origin, boolean worldgen, Block goo) {
		double radius = MIN_RADIUS + random.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
		int cx = origin.getX();
		int cz = origin.getZ();
		int cy = worldgen ? groundY(level, cx, cz, true) : origin.getY();
		int reach = Mth.ceil(radius + RIM_WIDTH + SCORCH_WIDTH);
		int size = reach * 2 + 1;
		int[] ground = new int[size * size];
		double[] distance = new double[size * size];
		// survey
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int i = (dx + reach) * size + dz + reach;
				double d = Math.sqrt(dx * dx + dz * dz) + (random.nextDouble() - 0.5) * EDGE_WOBBLE;
				distance[i] = d;
				if (d > radius + RIM_WIDTH + SCORCH_WIDTH) {
					continue;
				}
				int x = cx + dx;
				int z = cz + dz;
				int g = groundY(level, x, z, worldgen);
				ground[i] = g;
				if (!worldgen) {
					continue;
				}
				BlockPos top = new BlockPos(x, g, z);
				if (!level.ensureCanWrite(top) || !level.getFluidState(top).isEmpty() || !level.getFluidState(top.above()).isEmpty()) {
					return false;
				}
				if (d <= radius + RIM_WIDTH) {
					if (Math.abs(g - cy) > MAX_SLOPE) {
						return false;
					}
					for (int y = Math.min(g, cy) - DEPTH - 2; y <= Math.max(g, cy) + 3; y++) {
						BlockPos p = new BlockPos(x, y, z);
						if (!natural(level.getBlockState(p)) || level.getBlockEntity(p) != null) {
							return false;
						}
					}
				}
			}
		}
		int flags = worldgen ? Block.UPDATE_CLIENTS : Block.UPDATE_ALL;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int centerFloor = cy - DEPTH;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int i = (dx + reach) * size + dz + reach;
				double d = distance[i];
				int x = cx + dx;
				int z = cz + dz;
				int g = ground[i];
				if (d <= radius) {
					// the bowl
					double t = d / radius;
					int depth = (int)Math.round(DEPTH * (1.0 - t * t));
					int floor = Math.min(g, cy - depth);
					for (int y = floor + 1; y <= Math.max(g, cy) + 4; y++) {
						p.set(x, y, z);
						BlockState state = level.getBlockState(p);
						if (!state.isAir() && natural(state)) {
							level.setBlock(p, Blocks.AIR.defaultBlockState(), flags);
						}
					}
					put(level, p.set(x, floor, z), floorBlock(t, random), flags);
					put(level, p.set(x, floor - 1, z), random.nextFloat() < 0.6F ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(), flags);
				} else if (d <= radius + RIM_WIDTH) {
					// the lip of thrown-up debris
					put(level, p.set(x, g, z), random.nextFloat() < 0.55F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), flags);
					p.set(x, g + 1, z);
					if (random.nextFloat() < 0.45F && cover(level.getBlockState(p))) {
						level.setBlock(p, random.nextFloat() < 0.6F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), flags);
					} else {
						clearCover(level, p, flags);
					}
				} else if (d <= radius + RIM_WIDTH + SCORCH_WIDTH) {
					// burnt ground fading out
					double fade = 1.0 - (d - radius - RIM_WIDTH) / SCORCH_WIDTH;
					p.set(x, g, z);
					if (random.nextDouble() < fade * 0.75 && level.getBlockState(p).is(BlockTags.DIRT)) {
						level.setBlock(p, random.nextFloat() < 0.85F ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), flags);
						clearCover(level, p.set(x, g + 1, z), flags);
					}
				}
			}
		}
		// the heart: a hot, glassy seat with the goo on it
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				put(level, p.set(cx + dx, centerFloor, cz + dz), dx == 0 && dz == 0 ? Blocks.OBSIDIAN.defaultBlockState()
					: random.nextFloat() < 0.6F ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState(), flags);
			}
		}
		p.set(cx, centerFloor + 1, cz);
		level.setBlock(p, goo.defaultBlockState(), flags);
		return true;
	}

	private static BlockState floorBlock(double t, RandomSource random) {
		float r = random.nextFloat();
		if (t < 0.4) {
			return r < 0.3F ? Blocks.MAGMA_BLOCK.defaultBlockState() : r < 0.55F ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
		}
		return r < 0.5F ? Blocks.BLACKSTONE.defaultBlockState() : r < 0.85F ? Blocks.COARSE_DIRT.defaultBlockState()
			: r < 0.95F ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
	}

	/** Sets a block, but only over natural ground (the debug command may be used anywhere). */
	private static void put(WorldGenLevel level, BlockPos pos, BlockState state, int flags) {
		if (natural(level.getBlockState(pos))) {
			level.setBlock(pos, state, flags);
		}
	}

	private static void clearCover(WorldGenLevel level, BlockPos pos, int flags) {
		BlockState state = level.getBlockState(pos);
		if (!state.isAir() && cover(state) && state.getFluidState().isEmpty()) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
		}
	}

	/** The ground block of a column: the top solid block, under any plants, snow and leaves. */
	private static int groundY(WorldGenLevel level, int x, int z, boolean worldgen) {
		int y = level.getHeight(worldgen ? Heightmap.Types.OCEAN_FLOOR_WG : Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
		int min = level.getMinY();
		while (y > min && cover(level.getBlockState(p.setY(y)))) {
			y--;
		}
		return y;
	}

	/** Lying on top of the ground: air, plants, snow layers, leaves. */
	private static boolean cover(BlockState state) {
		return state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(Blocks.SNOW)
			|| state.canBeReplaced() && state.getFluidState().isEmpty();
	}

	/** Natural ground (and what grows on it): the only things a crater will dig through. */
	private static boolean natural(BlockState state) {
		return cover(state) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
			|| state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.SNOW) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
			|| state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.ICE)
			|| state.is(Blocks.COARSE_DIRT) || state.is(Blocks.BLACKSTONE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.OBSIDIAN);
	}
}
