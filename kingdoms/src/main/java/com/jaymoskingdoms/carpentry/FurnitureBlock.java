package com.jaymoskingdoms.carpentry;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Wooden benches and tables (after Better With Mods' bench and table): side by side they join into one long piece,
 * like fences, with legs only at its outer corners (a leg stands at a corner when neither side beside it joins on).
 * Benches join benches and tables tables, whatever their wood. Right-click a bench to sit on it ({@link BenchSeats}).
 */
public final class FurnitureBlock extends Block {
	public static final BooleanProperty NORTH = CrossCollisionBlock.NORTH;
	public static final BooleanProperty EAST = CrossCollisionBlock.EAST;
	public static final BooleanProperty SOUTH = CrossCollisionBlock.SOUTH;
	public static final BooleanProperty WEST = CrossCollisionBlock.WEST;
	private static final Map<Direction, BooleanProperty> SIDES = CrossCollisionBlock.PROPERTY_BY_DIRECTION;

	/** The bench's seat (its top is the cushion's height, half a block) and the table's top, in pixels. */
	static final double SEAT_BOTTOM = 5;
	static final double SEAT_TOP = 8;
	static final double TABLE_BOTTOM = 13;
	/** A leg's square, in from the corner. */
	static final double LEG_IN = 1;
	static final double LEG_OUT = 3;

	/** The shapes by which sides join (bit 0 north, 1 east, 2 south, 3 west). */
	private static final VoxelShape[] BENCH = shapes(SEAT_BOTTOM, SEAT_TOP);
	private static final VoxelShape[] TABLE = shapes(TABLE_BOTTOM, 16);

	final boolean bench;

	public FurnitureBlock(BlockBehaviour.Properties properties, boolean bench) {
		super(properties);
		this.bench = bench;
		this.registerDefaultState(this.stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
	}

	private static VoxelShape[] shapes(double bottom, double top) {
		VoxelShape[] out = new VoxelShape[16];
		for (int mask = 0; mask < 16; mask++) {
			boolean n = (mask & 1) != 0;
			boolean e = (mask & 2) != 0;
			boolean s = (mask & 4) != 0;
			boolean w = (mask & 8) != 0;
			VoxelShape shape = Block.box(0, bottom, 0, 16, top, 16);
			if (!n && !w) {
				shape = Shapes.or(shape, Block.box(LEG_IN, 0, LEG_IN, LEG_OUT, bottom, LEG_OUT));
			}
			if (!n && !e) {
				shape = Shapes.or(shape, Block.box(16 - LEG_OUT, 0, LEG_IN, 16 - LEG_IN, bottom, LEG_OUT));
			}
			if (!s && !e) {
				shape = Shapes.or(shape, Block.box(16 - LEG_OUT, 0, 16 - LEG_OUT, 16 - LEG_IN, bottom, 16 - LEG_IN));
			}
			if (!s && !w) {
				shape = Shapes.or(shape, Block.box(LEG_IN, 0, 16 - LEG_OUT, LEG_OUT, bottom, 16 - LEG_IN));
			}
			out[mask] = shape;
		}
		return out;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST);
	}

	/** Whether {@code other} is a piece of the same kind to join. */
	boolean joins(BlockState other) {
		return other.getBlock() instanceof FurnitureBlock f && f.bench == this.bench;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = this.defaultBlockState();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			state = state.setValue(SIDES.get(d), this.joins(context.getLevel().getBlockState(context.getClickedPos().relative(d))));
		}
		return state;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		if (direction.getAxis().isHorizontal()) {
			return state.setValue(SIDES.get(direction), this.joins(neighbour));
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		int mask = (state.getValue(NORTH) ? 1 : 0) | (state.getValue(EAST) ? 2 : 0) | (state.getValue(SOUTH) ? 4 : 0) | (state.getValue(WEST) ? 8 : 0);
		return (this.bench ? BENCH : TABLE)[mask];
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!this.bench || player.isSecondaryUseActive() || player.isPassenger()) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			return BenchSeats.sit(server, pos, player) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (this.bench) {
			BenchSeats.clear(level, pos);
		}
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return false;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		BlockState out = state;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			out = out.setValue(SIDES.get(rotation.rotate(d)), state.getValue(SIDES.get(d)));
		}
		return out;
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		BlockState out = state;
		for (Direction d : Direction.Plane.HORIZONTAL) {
			out = out.setValue(SIDES.get(mirror.mirror(d)), state.getValue(SIDES.get(d)));
		}
		return out;
	}
}
