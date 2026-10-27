package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Farmer's Delight's rope fence, MIT; their art, our own code.) A fence of rope between posts: it holds a lead like any
 * fence, joins only other rope fences, rope fence gates and solid sides, and is no use as a wooden fence's neighbour.
 */
public class RopeFenceBlock extends CrossCollisionBlock {
	public static final VoxelShape POST = Block.box(7.0F, 0.0F, 7.0F, 9.0F, 16.0F, 9.0F);

	public RopeFenceBlock(Properties properties) {
		super(1.0F, 16.0F, 1.0F, 16.0F, 24.0F, properties);
		registerDefaultState(stateDefinition.any()
			.setValue(NORTH, false)
			.setValue(EAST, false)
			.setValue(SOUTH, false)
			.setValue(WEST, false)
			.setValue(WATERLOGGED, false));
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (level.isClientSide()) {
			return player.getItemInHand(hand).is(Items.LEAD) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return LeadItem.bindPlayerMobs(player, level, pos);
	}

	@Override
	protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return POST;
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return POST;
	}

	public boolean connectsTo(BlockState state, boolean sideSolid, Direction direction) {
		boolean ropeGate = state.is(DelightFarm.ROPE_FENCE_GATE) && FenceGateBlock.connectsToDirection(state, direction);
		return !isExceptionForConnection(state) && sideSolid || isSameFence(state) || ropeGate;
	}

	protected boolean isSameFence(BlockState state) {
		return state.is(DelightFarm.ROPE_FENCE);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockGetter level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		FluidState fluid = context.getLevel().getFluidState(pos);
		BlockPos north = pos.north(), east = pos.east(), south = pos.south(), west = pos.west();
		BlockState northState = level.getBlockState(north), eastState = level.getBlockState(east);
		BlockState southState = level.getBlockState(south), westState = level.getBlockState(west);
		return super.getStateForPlacement(context)
			.setValue(NORTH, connectsTo(northState, northState.isFaceSturdy(level, north, Direction.SOUTH), Direction.SOUTH))
			.setValue(EAST, connectsTo(eastState, eastState.isFaceSturdy(level, east, Direction.WEST), Direction.WEST))
			.setValue(SOUTH, connectsTo(southState, southState.isFaceSturdy(level, south, Direction.NORTH), Direction.NORTH))
			.setValue(WEST, connectsTo(westState, westState.isFaceSturdy(level, west, Direction.EAST), Direction.EAST))
			.setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighborPos, BlockState neighbor, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return direction.getAxis().getPlane() == Direction.Plane.HORIZONTAL
			? state.setValue(PROPERTY_BY_DIRECTION.get(direction),
				connectsTo(neighbor, neighbor.isFaceSturdy(level, neighborPos, direction.getOpposite()), direction.getOpposite()))
			: super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, WEST, SOUTH, WATERLOGGED);
	}
}
