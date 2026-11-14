package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** What a picked berry bush leaves: bare stems. In light, a random tick now and then grows the bush back, sooner with a leaf pile beside it. */
public class BerryBushStemBlock extends Block {
	// ---- tuning ----
	/** A random tick regrows the bush with 1 chance in this many (the notes: 1 in 60), and needs this much light above. */
	public static final int REGROW_CHANCE = 60, REGROW_LIGHT = 9;
	/** With a leaf pile beside it the chance is 1 in this many, and the pile is used up. */
	public static final int MULCHED_REGROW_CHANCE = 6;

	private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 9.0, 13.0);

	public BerryBushStemBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return BerryBushBlock.survives(level, pos);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return direction == Direction.DOWN && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (level.getMaxLocalRawBrightness(pos.above()) < REGROW_LIGHT) {
			return;
		}
		BlockPos pile = ForestFloor.pileBeside(level, pos);
		if (random.nextInt(pile != null ? MULCHED_REGROW_CHANCE : REGROW_CHANCE) == 0) {
			if (pile != null) {
				ForestFloor.spend(level, pile);
			}
			level.setBlockAndUpdate(pos, AetherBlocks.BERRY_BUSH.defaultBlockState());
		}
	}
}
