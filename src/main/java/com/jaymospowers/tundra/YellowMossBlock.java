package com.jaymospowers.tundra;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Yellow moss, the second arrival on rock the lichen has broken down. It creeps onto dirt or coarse dirt beside it only
 * while glow lichen lives close by, and under open sky it now and then raises a shrub: a stunted red one where the moss
 * still lies on rock, an orange or yellow one where there is soil under it.
 */
public class YellowMossBlock extends Block {
	// ---- tuning ----
	/** One random tick in this many tries to creep (grass tries every tick; moss is slower). */
	public static final int SPREAD_CHANCE = 6;
	/** Glow lichen must be within this many blocks for the moss to creep. */
	public static final int LICHEN_NEAR = 4;
	/** One random tick in this many tries to raise a shrub. */
	public static final int SHRUB_CHANCE = 60;
	/** No more than SHRUB_CAP shrubs within SHRUB_RANGE blocks. */
	public static final int SHRUB_CAP = 3, SHRUB_RANGE = 5;

	public YellowMossBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(SPREAD_CHANCE) == 0) {
			this.creep(level, pos, random);
		}
		if (random.nextInt(SHRUB_CHANCE) == 0) {
			sprout(level, pos, random);
		}
	}

	/** Onto one dirt or coarse dirt block beside this one (level, a step up or a step down) with room over it. */
	private void creep(ServerLevel level, BlockPos pos, RandomSource random) {
		BlockPos target = pos.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random)).above(random.nextInt(3) - 1);
		BlockState there = level.getBlockState(target);
		if (!there.is(Blocks.DIRT) && !there.is(Blocks.COARSE_DIRT) || level.getBlockState(target.above()).isSolidRender()) {
			return;
		}
		if (count(level, pos, LICHEN_NEAR, Tundra.GLOW_LICHENS, 1) > 0) {
			level.setBlockAndUpdate(target, this.defaultBlockState());
		}
	}

	private static void sprout(ServerLevel level, BlockPos pos, RandomSource random) {
		BlockPos above = pos.above();
		if (!level.isEmptyBlock(above) || !level.canSeeSky(above)) {
			return;
		}
		int shrubs = count(level, pos, SHRUB_RANGE, Tundra.SHRUBS, SHRUB_CAP);
		if (shrubs < 0 || shrubs >= SHRUB_CAP) {
			return;
		}
		Block shrub = level.getBlockState(pos.below()).is(Tundra.LICHEN_TO_GRAVEL) ? Tundra.STUNTED_RED_SHRUB
			: random.nextBoolean() ? Tundra.ORANGE_SHRUB : Tundra.YELLOW_SHRUB;
		level.setBlockAndUpdate(above, shrub.defaultBlockState());
	}

	/**
	 * How many blocks of {@code tag} lie within {@code range} of {@code pos}, counting no further than {@code enough};
	 * -1 if part of that cube isn't loaded (nothing is loaded to find out).
	 */
	static int count(ServerLevel level, BlockPos pos, int range, TagKey<Block> tag, int enough) {
		BlockPos low = pos.offset(-range, -range, -range), high = pos.offset(range, range, range);
		if (!level.hasChunksAt(low, high)) {
			return -1;
		}
		int found = 0;
		for (BlockPos spot : BlockPos.betweenClosed(low, high)) {
			if (level.getBlockState(spot).is(tag) && ++found >= enough) {
				break;
			}
		}
		return found;
	}
}
