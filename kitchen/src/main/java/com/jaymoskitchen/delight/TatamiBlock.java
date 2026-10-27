package com.jaymoskitchen.delight;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (Farmer's Delight's tatami, MIT; their models and art, our own code.) The tatami block is a straw-mat cube that
 * pairs with the one it's placed against (unless you sneak); {@link Mat} is the full-length mat (two blocks, like a bed)
 * and {@link Half} the half mat.
 */
public class TatamiBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	public static final BooleanProperty PAIRED = BooleanProperty.create("paired");
	/** Mats are this thick (a sixteenth of a block each pixel). */
	private static final VoxelShape MAT_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0);

	public TatamiBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN).setValue(PAIRED, false));
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		BlockState target = context.getLevel().getBlockState(context.getClickedPos().relative(face.getOpposite()));
		boolean pairing = context.getPlayer() != null && !context.getPlayer().isShiftKeyDown() && target.getBlock() == this && !target.getValue(PAIRED);
		return defaultBlockState().setValue(FACING, face.getOpposite()).setValue(PAIRED, pairing);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide() || (placer != null && placer.isShiftKeyDown())) {
			return;
		}
		BlockPos facingPos = pos.relative(state.getValue(FACING));
		BlockState facingState = level.getBlockState(facingPos);
		if (facingState.getBlock() == this && !facingState.getValue(PAIRED)) {
			level.setBlock(facingPos, state.setValue(FACING, state.getValue(FACING).getOpposite()).setValue(PAIRED, true), 3);
			level.updateNeighborsAt(pos, Blocks.AIR);
			state.updateNeighbourShapes(level, pos, 3);
		}
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		if (direction.equals(state.getValue(FACING)) && state.getValue(PAIRED) && level.getBlockState(neighbourPos).getBlock() != this) {
			return state.setValue(PAIRED, false);
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, PAIRED);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** The full mat: a foot and a head block, placed together, broken together. */
	public static class Mat extends HorizontalDirectionalBlock {
		public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;

		public Mat(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT));
		}

		private static Direction toOther(BedPart part, Direction facing) {
			return part == BedPart.FOOT ? facing : facing.getOpposite();
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING, PART);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return MAT_SHAPE;
		}

		@Override
		protected RenderShape getRenderShape(BlockState state) {
			return RenderShape.MODEL;
		}

		@Override
		protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
			if (direction == toOther(state.getValue(PART), state.getValue(FACING))) {
				return state.canSurvive(level, pos) && neighbour.is(this) && neighbour.getValue(PART) != state.getValue(PART) ? state : Blocks.AIR.defaultBlockState();
			}
			return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
		}

		@Override
		protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
			return !level.isEmptyBlock(pos.below());
		}

		@Override
		public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
			if (!level.isClientSide() && player.isCreative() && state.getValue(PART) == BedPart.FOOT) {
				BlockPos pairPos = pos.relative(toOther(BedPart.FOOT, state.getValue(FACING)));
				BlockState pair = level.getBlockState(pairPos);
				if (pair.getBlock() == this && pair.getValue(PART) == BedPart.HEAD) {
					level.setBlock(pairPos, Blocks.AIR.defaultBlockState(), 35);
					level.levelEvent(player, 2001, pairPos, Block.getId(pair));
				}
			}
			return super.playerWillDestroy(level, pos, state, player);
		}

		@Override
		public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
			super.setPlacedBy(level, pos, state, placer, stack);
			if (!level.isClientSide()) {
				level.setBlock(pos.relative(state.getValue(FACING)), state.setValue(PART, BedPart.HEAD), 3);
				level.updateNeighborsAt(pos, Blocks.AIR);
				state.updateNeighbourShapes(level, pos, 3);
			}
		}

		@Override
		public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
			Direction facing = context.getHorizontalDirection();
			BlockPos pairPos = context.getClickedPos().relative(facing);
			BlockState pair = context.getLevel().getBlockState(pairPos);
			if (pair.canBeReplaced(context) && canSurvive(pair, context.getLevel(), pairPos)) {
				return defaultBlockState().setValue(FACING, facing);
			}
			return null;
		}
	}

	/** The half mat: one block, a mat's width. */
	public static class Half extends HorizontalDirectionalBlock {
		public Half(Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return MAT_SHAPE;
		}

		@Override
		protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
			return !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
		}

		@Override
		protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
			return !level.isEmptyBlock(pos.below());
		}

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext context) {
			return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(FACING);
		}
	}
}
