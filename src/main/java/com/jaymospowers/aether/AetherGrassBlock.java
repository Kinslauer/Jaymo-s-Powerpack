package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Aether grass: spreads onto aether dirt like vanilla grass onto dirt (needs light above the dirt), and turns back to
 * aether dirt when something solid covers it. The enchanted kind ({@link AetherBlocks#ENCHANTED_AETHER_GRASS_BLOCK})
 * also turns back when covered, but never spreads.
 */
public class AetherGrassBlock extends Block {
	// ---- tuning ----
	/** Light needed above a dirt block for the grass to spread onto it. */
	public static final int SPREAD_LIGHT = 9;
	/**
	 * Spread attempts per random tick, and how far around (x and z) and below/above (y) they reach: a 3x5x3 box, as in
	 * The Aether II. Each attempt that finds bare dirt converts it with a 1 in {@link #SPREAD_ODDS} chance.
	 */
	public static final int SPREAD_TRIES = 4, SPREAD_RADIUS = 1, SPREAD_DOWN = 3, SPREAD_UP = 1, SPREAD_ODDS = 10;

	private final boolean spreads;

	public AetherGrassBlock(boolean spreads, BlockBehaviour.Properties properties) {
		super(properties);
		this.spreads = spreads;
	}

	/** Something solid sits on this block's top (the grass can't live there). */
	private static boolean smothered(ServerLevel level, BlockPos pos) {
		BlockPos above = pos.above();
		return level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (smothered(level, pos)) {
			level.setBlockAndUpdate(pos, AetherBlocks.AETHER_DIRT.defaultBlockState());
			return;
		}
		if (!this.spreads || level.getMaxLocalRawBrightness(pos.above()) < SPREAD_LIGHT) {
			return;
		}
		for (int i = 0; i < SPREAD_TRIES; i++) {
			BlockPos target = pos.offset(random.nextInt(2 * SPREAD_RADIUS + 1) - SPREAD_RADIUS,
				random.nextInt(SPREAD_DOWN + SPREAD_UP + 1) - SPREAD_DOWN, random.nextInt(2 * SPREAD_RADIUS + 1) - SPREAD_RADIUS);
			if (random.nextInt(SPREAD_ODDS) == 0 && level.getBlockState(target).is(AetherBlocks.AETHER_DIRT) && !smothered(level, target)
				&& level.getMaxLocalRawBrightness(target.above()) >= SPREAD_LIGHT) {
				level.setBlockAndUpdate(target, this.defaultBlockState());
			}
		}
	}
}
