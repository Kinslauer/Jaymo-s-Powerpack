package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (Farmer's Delight's rope, MIT; their art, our own code.) A rope hung from a block (or tied to a bell): climbable
 * (the {@code climbable} tag), tomatoes climb it ({@link TomatoBlock}). Sneak with an empty hand to reel it back in from
 * the bottom; use it on a rope to add to the end of it ({@link RopeItem}); and it rings a bell hung at its top.
 */
public class RopeBlock extends IronBarsBlock {
	public static final BooleanProperty TIED_TO_BELL = BooleanProperty.create("tied_to_bell");
	private static final VoxelShape LOWER_SUPPORT_AABB = Block.box(7, 0, 7, 9, 1, 9);
	/** How far up a bell can hang and still be rung through the rope. */
	private static final int BELL_REACH = 24;

	public RopeBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
			.setValue(CrossCollisionBlock.NORTH, false)
			.setValue(CrossCollisionBlock.SOUTH, false)
			.setValue(CrossCollisionBlock.EAST, false)
			.setValue(CrossCollisionBlock.WEST, false)
			.setValue(TIED_TO_BELL, false)
			.setValue(CrossCollisionBlock.WATERLOGGED, false));
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return true;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (stack.is(DelightFarm.ROPE.asItem())) {
			return InteractionResult.PASS;
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player.isSecondaryUseActive()) {
			if (player.getAbilities().mayBuild && (player.getAbilities().instabuild || player.getInventory().add(new ItemStack(asItem())))) {
				BlockPos.MutableBlockPos reeling = pos.mutable().move(Direction.DOWN);
				int minY = level.getMinY();
				while (reeling.getY() >= minY - 1) {
					if (level.getBlockState(reeling).is(this)) {
						reeling.move(Direction.DOWN);
					} else {
						// the bottom rope of the run comes off
						reeling.move(Direction.UP);
						level.destroyBlock(reeling, false, player);
						return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
					}
				}
			}
		} else {
			BlockPos.MutableBlockPos ringing = pos.mutable().move(Direction.UP);
			for (int i = 0; i < BELL_REACH; i++) {
				BlockState above = level.getBlockState(ringing);
				if (above.getBlock() == Blocks.BELL) {
					((BellBlock) above.getBlock()).attemptToRing(level, ringing, above.getValue(BellBlock.FACING).getClockWise());
					return InteractionResult.SUCCESS;
				} else if (above.getBlock() == this) {
					ringing.move(Direction.UP);
				} else {
					return InteractionResult.PASS;
				}
			}
		}
		return InteractionResult.PASS;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return getStateWithConnections(defaultBlockState(), context.getLevel(), context.getClickedPos(), context.getClickedFace());
	}

	/** The rope's state at a spot: tied to a bell above, joined to sturdy sides (placed from the side) or to ropes, bars and walls. */
	public static BlockState getStateWithConnections(BlockState state, LevelAccessor level, BlockPos pos, Direction clickedFace) {
		FluidState fluid = level.getFluidState(pos);
		BlockPos north = pos.north(), south = pos.south(), west = pos.west(), east = pos.east();
		BlockState northState = level.getBlockState(north), southState = level.getBlockState(south);
		BlockState westState = level.getBlockState(west), eastState = level.getBlockState(east);
		boolean horizontal = clickedFace.getAxis().isHorizontal();
		return state.setValue(TIED_TO_BELL, level.getBlockState(pos.above()).getBlock() == Blocks.BELL)
			.setValue(NORTH, horizontal ? tieToAnythingValid(northState, northState.isFaceSturdy(level, north, Direction.SOUTH)) : tieToRopeAndWalls(northState))
			.setValue(SOUTH, horizontal ? tieToAnythingValid(southState, southState.isFaceSturdy(level, south, Direction.NORTH)) : tieToRopeAndWalls(southState))
			.setValue(WEST, horizontal ? tieToAnythingValid(westState, westState.isFaceSturdy(level, west, Direction.EAST)) : tieToRopeAndWalls(westState))
			.setValue(EAST, horizontal ? tieToAnythingValid(eastState, eastState.isFaceSturdy(level, east, Direction.WEST)) : tieToRopeAndWalls(eastState))
			.setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
	}

	public static boolean tieToAnythingValid(BlockState state, boolean solidSide) {
		return !isExceptionForConnection(state) && solidSide || tieToRopeAndWalls(state);
	}

	public static boolean tieToRopeAndWalls(BlockState state) {
		return state.getBlock() instanceof IronBarsBlock || state.is(BlockTags.WALLS);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
		return LOWER_SUPPORT_AABB;
	}

	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return context.getItemInHand().getItem() == asItem();
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction facing,
		BlockPos facingPos, BlockState facingState, RandomSource random) {
		if (state.getValue(CrossCollisionBlock.WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		boolean tiedToBell = state.getValue(TIED_TO_BELL);
		if (facing == Direction.UP) {
			tiedToBell = level.getBlockState(facingPos).getBlock() == Blocks.BELL;
		}
		return facing.getAxis().isHorizontal()
			? state.setValue(TIED_TO_BELL, tiedToBell).setValue(CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(facing),
				attachsTo(facingState, facingState.isFaceSturdy(level, facingPos, facing.getOpposite())))
			: super.updateShape(state.setValue(TIED_TO_BELL, tiedToBell), level, ticks, pos, facing, facingPos, facingState, random);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, WEST, SOUTH, WATERLOGGED, TIED_TO_BELL);
	}
}
