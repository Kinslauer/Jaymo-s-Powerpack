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
 * Corner: an eighth of a block, in the {@link Octant} of the block's space you clicked in (against the
 * clicked face).
 */
public final class CornerBlock extends TrimBlock {
	/** The block's eight corners, named by their three sides. */
	public enum Octant implements StringRepresentable {
		DOWN_NORTH_WEST(Direction.DOWN, Direction.NORTH, Direction.WEST),
		DOWN_NORTH_EAST(Direction.DOWN, Direction.NORTH, Direction.EAST),
		DOWN_SOUTH_WEST(Direction.DOWN, Direction.SOUTH, Direction.WEST),
		DOWN_SOUTH_EAST(Direction.DOWN, Direction.SOUTH, Direction.EAST),
		UP_NORTH_WEST(Direction.UP, Direction.NORTH, Direction.WEST),
		UP_NORTH_EAST(Direction.UP, Direction.NORTH, Direction.EAST),
		UP_SOUTH_WEST(Direction.UP, Direction.SOUTH, Direction.WEST),
		UP_SOUTH_EAST(Direction.UP, Direction.SOUTH, Direction.EAST);

		final Direction y;
		final Direction z;
		final Direction x;
		final VoxelShape shape;

		Octant(Direction y, Direction z, Direction x) {
			this.y = y;
			this.z = z;
			this.x = x;
			this.shape = cut(y, z, x);
		}

		/** The corner toward three sides, one on each axis (in any order). */
		static Octant of(Direction p, Direction q, Direction r) {
			Direction y = Direction.DOWN;
			Direction z = Direction.NORTH;
			Direction x = Direction.WEST;
			for (Direction d : new Direction[] {p, q, r}) {
				switch (d.getAxis()) {
					case Y -> y = d;
					case Z -> z = d;
					case X -> x = d;
				}
			}
			for (Octant o : values()) {
				if (o.y == y && o.z == z && o.x == x) {
					return o;
				}
			}
			return DOWN_NORTH_WEST;
		}

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	public static final EnumProperty<Octant> CORNER = EnumProperty.create("corner", Octant.class);

	public CornerBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(CORNER, Octant.DOWN_NORTH_WEST).setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CORNER, WATERLOGGED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Direction face = context.getClickedFace();
		Vec3 local = local(context);
		Direction[] sides = new Direction[3];
		int i = 0;
		for (Direction.Axis axis : Direction.Axis.values()) {
			sides[i++] = axis == face.getAxis() ? face.getOpposite() : side(local, axis);
		}
		return wet(this.defaultBlockState().setValue(CORNER, Octant.of(sides[0], sides[1], sides[2])), context);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(CORNER).shape;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		Octant o = state.getValue(CORNER);
		return state.setValue(CORNER, Octant.of(o.y, rotation.rotate(o.z), rotation.rotate(o.x)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		Octant o = state.getValue(CORNER);
		return state.setValue(CORNER, Octant.of(o.y, mirror.mirror(o.z), mirror.mirror(o.x)));
	}
}
