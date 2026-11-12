package com.jaymospowers.eden;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The head of an aquatus, the plant that drinks the land dry: now and then it turns one block of soil within
 * {@link #REACH} of it to sand (or gravel), so every aquatus by a lake comes to stand in its own ring of desert.
 * Farmland and anything growing a crop are spared only by distance: plant them further off.
 */
public class AquatusBlock extends Block {
	// ---- tuning ----
	/** How far it dries the ground (blocks out, and up or down), and the share of dried soil that becomes gravel. */
	public static final int REACH = 5, REACH_Y = 2;
	public static final float GRAVEL_SHARE = 0.18F;

	public AquatusBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockPos at = pos.offset(random.nextInt(REACH * 2 + 1) - REACH, random.nextInt(REACH_Y * 2 + 1) - REACH_Y - 1, random.nextInt(REACH * 2 + 1) - REACH);
		BlockState soil = level.getBlockState(at);
		if (soil.is(BlockTags.DIRT) && !soil.is(Eden.MYCELIUM) && !level.getBlockState(at.above()).is(Eden.AQUATUS_ROOTS)) {
			level.setBlock(at, (random.nextFloat() < GRAVEL_SHARE ? Blocks.GRAVEL : Blocks.SAND).defaultBlockState(), Block.UPDATE_ALL);
		}
	}
}
