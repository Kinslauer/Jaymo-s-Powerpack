package com.jaymospowers.frost;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Where the 26.4 ice shows up in a 26.3 world (we have no ice caves, so it goes where the cold already is), placed in
 * snowy and icy overworld biomes ({@code worldgen/placed_feature/frozen_cave_patch.json} and {@code thin_ice.json}):
 * <ul>
 * <li>{@link FrozenCave}: in a cave under the snow, a patch of the ceiling turns to packed ice with icicles hanging from
 * it, and the floor below it to packed ice with ice crystals and a little snow.</li>
 * <li>{@link ThinIce}: on a frozen lake, a blob of the surface ice is brittle.</li>
 * </ul>
 */
public final class FrostFeatures {
	// ---- tuning ----
	/** How far a cave patch looks up for its ceiling and down for its floor, and the patch radius (blocks). */
	public static final int CAVE_SEEK = 10, CAVE_RADIUS = 3;
	/** Of the ceiling patch, this many in 10 grow an icicle (1 to {@link IcicleBlock#MAX_LENGTH} - 1 long). */
	public static final int ICICLE_SHARE = 4;
	/** Of the floor patch, this many in 10 grow a crystal, and this many in 10 a snow layer. */
	public static final int CRYSTAL_SHARE = 2, SNOW_SHARE = 2;
	/** Thin ice blob radius (blocks, from 2 to this). */
	public static final int THIN_RADIUS = 4;

	private FrostFeatures() {
	}

	/** Natural rock a frozen patch may take over (never a build). */
	private static boolean rock(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.DIRT) || state.is(Blocks.GRAVEL) || state.is(BlockTags.ICE);
	}

	public record FrozenCave() implements Feature {
		public static final MapCodec<FrozenCave> CODEC = MapCodec.unit(FrozenCave::new);

		@Override
		public MapCodec<FrozenCave> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			if (!level.getBlockState(origin).isAir()) {
				return false;
			}
			BlockPos ceiling = null, floor = null;
			for (int i = 1; i <= CAVE_SEEK && ceiling == null; i++) {
				if (!level.getBlockState(origin.above(i)).isAir()) {
					ceiling = origin.above(i);
				}
			}
			for (int i = 1; i <= CAVE_SEEK && floor == null; i++) {
				if (!level.getBlockState(origin.below(i)).isAir()) {
					floor = origin.below(i);
				}
			}
			// (open sky above is no cave)
			if (ceiling == null || floor == null || origin.getY() >= level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX(), origin.getZ())) {
				return false;
			}
			boolean placed = false;
			for (int dx = -CAVE_RADIUS; dx <= CAVE_RADIUS; dx++) {
				for (int dz = -CAVE_RADIUS; dz <= CAVE_RADIUS; dz++) {
					if (dx * dx + dz * dz > CAVE_RADIUS * CAVE_RADIUS + random.nextInt(3)) {
						continue;
					}
					placed |= this.ceiling(level, random, ceiling.offset(dx, 0, dz));
					placed |= this.floor(level, random, floor.offset(dx, 0, dz));
				}
			}
			return placed;
		}

		/** The ceiling block over this column (within a block or two of the patch height) turns to ice; maybe an icicle. */
		private boolean ceiling(WorldGenLevel level, RandomSource random, BlockPos near) {
			for (int dy = -2; dy <= 2; dy++) {
				BlockPos pos = near.above(dy);
				if (rock(level.getBlockState(pos)) && level.getBlockState(pos.below()).isAir()) {
					level.setBlock(pos, Blocks.PACKED_ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
					if (random.nextInt(10) < ICICLE_SHARE) {
						int length = 1 + random.nextInt(IcicleBlock.MAX_LENGTH - 1);
						for (int i = 1; i <= length; i++) {
							BlockPos at = pos.below(i);
							if (!level.getBlockState(at).isAir() || !level.getBlockState(at.below()).isAir()) {
								length = i - 1;
								break;
							}
						}
						for (int i = 1; i <= length; i++) {
							IcicleBlock.Part part = i == length ? IcicleBlock.Part.TIP : i == 1 ? IcicleBlock.Part.BASE : IcicleBlock.Part.MIDDLE;
							level.setBlock(pos.below(i), Frost.ICICLE.defaultBlockState().setValue(IcicleBlock.TIP_DIRECTION, Direction.DOWN)
								.setValue(IcicleBlock.PART, part), Block.UPDATE_CLIENTS);
						}
					}
					return true;
				}
			}
			return false;
		}

		/** The floor block under this column turns to packed ice; maybe a crystal or a little snow on it. */
		private boolean floor(WorldGenLevel level, RandomSource random, BlockPos near) {
			for (int dy = 2; dy >= -2; dy--) {
				BlockPos pos = near.above(dy);
				if (rock(level.getBlockState(pos)) && level.getBlockState(pos.above()).isAir()) {
					level.setBlock(pos, Blocks.PACKED_ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
					int roll = random.nextInt(10);
					if (roll < CRYSTAL_SHARE) {
						level.setBlock(pos.above(), Frost.ICE_CRYSTAL.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP), Block.UPDATE_CLIENTS);
					} else if (roll < CRYSTAL_SHARE + SNOW_SHARE) {
						level.setBlock(pos.above(), Blocks.SNOW.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
					return true;
				}
			}
			return false;
		}
	}

	public record ThinIce() implements Feature {
		public static final MapCodec<ThinIce> CODEC = MapCodec.unit(ThinIce::new);

		@Override
		public MapCodec<ThinIce> codec() {
			return CODEC;
		}

		/** {@code origin} is the first open block over the ground (the heightmap): the lake's ice is just under it. */
		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
			BlockPos centre = origin.below();
			if (!frozenWater(level, centre)) {
				return false;
			}
			int radius = 2 + random.nextInt(THIN_RADIUS - 1);
			boolean placed = false;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					BlockPos pos = centre.offset(dx, 0, dz);
					if (dx * dx + dz * dz <= radius * radius - random.nextInt(2) && frozenWater(level, pos)) {
						level.setBlock(pos, Frost.BRITTLE_ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
						placed = true;
					}
				}
			}
			return placed;
		}

		private static boolean frozenWater(WorldGenLevel level, BlockPos pos) {
			return level.getBlockState(pos).is(Blocks.ICE) && level.getFluidState(pos.below()).is(FluidTags.WATER);
		}
	}
}
