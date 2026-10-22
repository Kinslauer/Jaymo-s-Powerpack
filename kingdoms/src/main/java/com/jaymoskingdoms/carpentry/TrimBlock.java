package com.jaymoskingdoms.carpentry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The timber trim's common ground (after Better With Mods' siding, moulding and corner): a piece of a block
 * cut from a vanilla material, waterloggable like a slab, placed by where on the face you click.
 */
abstract class TrimBlock extends Block implements SimpleWaterloggedBlock {
	static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	/** A click this close to a face's middle (in blocks, either way) counts as the middle. */
	static final double MIDDLE = 0.25;

	TrimBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	/** Where in the new block's space the click landed, each axis 0 to 1. */
	static Vec3 local(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		Vec3 hit = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
		return new Vec3(clamp(hit.x), clamp(hit.y), clamp(hit.z));
	}

	private static double clamp(double v) {
		return Math.max(0.0, Math.min(1.0, v));
	}

	/** The half of {@code axis} the click is in. */
	static Direction side(Vec3 local, Direction.Axis axis) {
		return Direction.fromAxisAndDirection(axis, local.get(axis) >= 0.5 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
	}

	/** The in-face axis the click is farthest along from the middle (the face being the clicked one). */
	static Direction.Axis farthest(Vec3 local, Direction.Axis face) {
		Direction.Axis best = null;
		double far = -1.0;
		for (Direction.Axis axis : Direction.Axis.values()) {
			double off = Math.abs(local.get(axis) - 0.5);
			if (axis != face && off > far) {
				far = off;
				best = axis;
			}
		}
		return best;
	}

	/** The box of the halves toward each of {@code sides} (a siding is one half, a moulding two, a corner three). */
	static VoxelShape cut(Direction... sides) {
		double[] min = {0, 0, 0};
		double[] max = {16, 16, 16};
		for (Direction d : sides) {
			int i = d.getAxis().ordinal();
			if (d.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
				min[i] = 8;
			} else {
				max[i] = 8;
			}
		}
		return Block.box(min[0], min[1], min[2], max[0], max[1], max[2]);
	}

	/** {@code state} with water in it if it's placed in water. */
	BlockState wet(BlockState state, BlockPlaceContext context) {
		return state.setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return type == PathComputationType.WATER && state.getFluidState().isSource();
	}
}
