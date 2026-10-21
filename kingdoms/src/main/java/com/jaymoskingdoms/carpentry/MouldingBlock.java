package com.jaymoskingdoms.carpentry;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Moulding: a quarter block, a beam along one of the block's twelve {@link Edge edges}. It lies against the
 * clicked face, along the edge nearest where you clicked.
 */
public final class MouldingBlock extends TrimBlock {
	/** The twelve edges, each named by the two sides it runs between. */
	public enum Edge implements StringRepresentable {
		DOWN_NORTH(Direction.DOWN, Direction.NORTH),
		DOWN_SOUTH(Direction.DOWN, Direction.SOUTH),
		DOWN_WEST(Direction.DOWN, Direction.WEST),
		DOWN_EAST(Direction.DOWN, Direction.EAST),
		UP_NORTH(Direction.UP, Direction.NORTH),
		UP_SOUTH(Direction.UP, Direction.SOUTH),
		UP_WEST(Direction.UP, Direction.WEST),
		UP_EAST(Direction.UP, Direction.EAST),
		NORTH_WEST(Direction.NORTH, Direction.WEST),
		NORTH_EAST(Direction.NORTH, Direction.EAST),
		SOUTH_WEST(Direction.SOUTH, Direction.WEST),
		SOUTH_EAST(Direction.SOUTH, Direction.EAST);

		final Direction a;
		final Direction b;
		final VoxelShape shape;

		Edge(Direction a, Direction b) {
			this.a = a;
			this.b = b;
			this.shape = cut(a, b);
		}

		/** The edge between two sides on different axes (in either order). */
		static Edge of(Direction x, Direction y) {
			for (Edge e : values()) {
				if ((e.a == x && e.b == y) || (e.a == y && e.b == x)) {
					return e;
				}
			}
			return DOWN_NORTH;
		}

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	public static final EnumProperty<Edge> EDGE = EnumProperty.create("edge", Edge.class);

	public MouldingBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(EDGE, Edge.DOWN_NORTH).setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(EDGE, WATERLOGGED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		Vec3 local = local(context);
		Edge edge = Edge.of(face.getOpposite(), side(local, farthest(local, face.getAxis())));
		return wet(this.defaultBlockState().setValue(EDGE, edge), context);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(EDGE).shape;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		Edge e = state.getValue(EDGE);
		return state.setValue(EDGE, Edge.of(rotation.rotate(e.a), rotation.rotate(e.b)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		Edge e = state.getValue(EDGE);
		return state.setValue(EDGE, Edge.of(mirror.mirror(e.a), mirror.mirror(e.b)));
	}
}
