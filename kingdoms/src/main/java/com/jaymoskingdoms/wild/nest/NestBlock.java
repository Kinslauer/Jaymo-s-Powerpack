package com.jaymoskingdoms.wild.nest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (medieval picks, de-nests) What a spider nest is made of that burns: its nest silk (a full block of matted silk over the
 * walls and roof) and the shed skins on its floor (a thin skin lying on the ground, {@code lying}). Either one burning marks
 * its nest burned out for good ({@link SpiderNests#scar}).
 */
public class NestBlock extends Block {
	private static final VoxelShape SKIN = Block.box(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);
	private final boolean lying;

	public NestBlock(Properties properties, boolean lying) {
		super(properties);
		this.lying = lying;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.lying ? SKIN : Shapes.block();
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return !this.lying || !level.isEmptyBlock(pos.below());
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos,
			BlockState neighbourState, RandomSource random) {
		if (this.lying && direction == Direction.DOWN && !state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		SpiderNests.burned(level, pos);
	}
}
