package com.jaymospowers.buildblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A quarter step: a beam {@value #SIZE} px wide and tall and a block long, lying along {@link #AXIS}. A block holds up to
 * four, one in each corner of its cross-section (low or high, and on the negative or positive side of the other
 * horizontal axis, so west or east of a north-south beam); a fifth is never needed, and four are the full block. A new
 * one goes into the corner the cursor is in; onto a block that already has some it fills the free corner beyond the face
 * that was clicked. Waterlogs until it is full.
 */
public class QuarterStepBlock extends Block implements SimpleWaterloggedBlock {
	/** Width and height of one beam, in pixels. */
	public static final int SIZE = 8;
	/** Within this much of a block's edge (0 to 1) a click on the face of a stored block is on its border, not inside it. */
	private static final double BORDER = 0.01;

	public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
	public static final BooleanProperty LOW_NEG = BooleanProperty.create("low_neg");
	public static final BooleanProperty LOW_POS = BooleanProperty.create("low_pos");
	public static final BooleanProperty HIGH_NEG = BooleanProperty.create("high_neg");
	public static final BooleanProperty HIGH_POS = BooleanProperty.create("high_pos");
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	/** Indexed by {@code high * 2 + pos}. */
	private static final BooleanProperty[] CORNERS = {LOW_NEG, LOW_POS, HIGH_NEG, HIGH_POS};
	/** [beam along z (0) or x (1)][which corners are filled, as bits of the CORNERS index]. */
	private static final VoxelShape[][] SHAPES = new VoxelShape[2][16];

	static {
		int[] across = {0, SIZE, 0, SIZE};
		int[] up = {0, 0, SIZE, SIZE};
		for (int mask = 0; mask < 16; mask++) {
			VoxelShape alongZ = Shapes.empty(), alongX = Shapes.empty();
			for (int i = 0; i < 4; i++) {
				if ((mask & (1 << i)) != 0) {
					alongZ = Shapes.or(alongZ, Block.box(across[i], up[i], 0, across[i] + SIZE, up[i] + SIZE, 16));
					alongX = Shapes.or(alongX, Block.box(0, up[i], across[i], 16, up[i] + SIZE, across[i] + SIZE));
				}
			}
			SHAPES[0][mask] = alongZ;
			SHAPES[1][mask] = alongX;
		}
	}

	public QuarterStepBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(LOW_NEG, true).setValue(LOW_POS, false)
			.setValue(HIGH_NEG, false).setValue(HIGH_POS, false).setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AXIS, LOW_NEG, LOW_POS, HIGH_NEG, HIGH_POS, WATERLOGGED);
	}

	private static int mask(BlockState state) {
		int mask = 0;
		for (int i = 0; i < 4; i++) {
			if (state.getValue(CORNERS[i])) {
				mask |= 1 << i;
			}
		}
		return mask;
	}

	private static boolean full(BlockState state) {
		return mask(state) == 15;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES[state.getValue(AXIS) == Direction.Axis.Z ? 0 : 1][mask(state)];
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	/**
	 * Which corner a click aims at, as an index into {@link #CORNERS}, or -1 for none. {@code onBlock}: the block already
	 * holds steps and the click was on one of them (its face then points into the corner wanted); otherwise the block is
	 * the new one, put beside the block that was clicked.
	 */
	private static int corner(BlockPlaceContext context, Direction.Axis beam, boolean onBlock) {
		Direction face = context.getClickedFace();
		Vec3 hit = context.getClickLocation();
		BlockPos pos = context.getClickedPos();
		Direction.Axis acrossAxis = beam == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z;
		int high = pick(face, Direction.Axis.Y, hit, pos, onBlock);
		int positive = pick(face, acrossAxis, hit, pos, onBlock);
		if (face.getAxis() == beam && onBlock || high < 0 || positive < 0) {
			return -1;
		}
		return high * 2 + positive;
	}

	/** 1 for the high (positive) side along {@code axis}, 0 for the low one, -1 when the click can't mean either. */
	private static int pick(Direction face, Direction.Axis axis, Vec3 hit, BlockPos pos, boolean onBlock) {
		double at = hit.get(axis) - pos.get(axis);
		if (face.getAxis() == axis) {
			boolean positiveFace = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
			if (onBlock) {
				// the face of a step inside the block points into the free corner; on the block's own edge it points out of it
				return at > BORDER && at < 1 - BORDER ? (positiveFace ? 1 : 0) : -1;
			}
			// the block beside the one clicked: the corner touching the clicked block
			return positiveFace ? 0 : 1;
		}
		return at > 0.5 ? 1 : 0;
	}

	/** Only the same step goes into a block of its own, into a free corner. */
	@Override
	protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		if (full(state) || !context.getItemInHand().is(asItem())) {
			return false;
		}
		int corner = corner(context, state.getValue(AXIS), context.replacingClickedOnBlock());
		return corner >= 0 && !state.getValue(CORNERS[corner]);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState there = context.getLevel().getBlockState(context.getClickedPos());
		if (there.is(this)) {
			int corner = corner(context, there.getValue(AXIS), context.replacingClickedOnBlock());
			BlockState now = corner < 0 ? there : there.setValue(CORNERS[corner], true);
			return full(now) ? now.setValue(WATERLOGGED, false) : now;
		}
		Direction.Axis beam = context.getHorizontalDirection().getClockWise().getAxis();
		int corner = Math.max(0, corner(context, beam, false));
		boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
		BlockState state = defaultBlockState().setValue(AXIS, beam).setValue(WATERLOGGED, water);
		for (int i = 0; i < 4; i++) {
			state = state.setValue(CORNERS[i], i == corner);
		}
		return state;
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@Override
	public boolean canPlaceLiquid(LivingEntity user, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
		return !full(state) && SimpleWaterloggedBlock.super.canPlaceLiquid(user, level, pos, state, fluid);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		if (state.getValue(WATERLOGGED)) {
			ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	/** A quarter turn swaps the beam's axis; the corners keep their names (negative and positive side), which is close enough for a rotated structure. */
	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
			? state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.Z ? Direction.Axis.X : Direction.Axis.Z) : state;
	}
}
