package com.jaymospowers.eden;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The pulse tree: a thin glowing stem, in places carrying a flat cap (small, medium or big). It stands like bamboo, on
 * soil, on a pulse tree log or on more of itself, and comes down from the break upward. The glow's slow beat is in its
 * textures, which all animate on the game's one clock, so every pulse tree in sight brightens and dims together.
 */
public class PulseTreeBlock extends Block {
	public enum Shape implements StringRepresentable {
		STEM("stem"), HEAD_SMALL("head_small"), HEAD_MEDIUM("head_medium"), HEAD_BIG("head_big");

		private final String name;

		Shape(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}

	public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

	private static final VoxelShape STEM = Block.box(6, 0, 6, 10, 16, 10);
	private static final VoxelShape SMALL = Shapes.or(STEM, Block.box(4, 12, 4, 12, 16, 12));
	private static final VoxelShape MEDIUM = Shapes.or(STEM, Block.box(2, 12, 2, 14, 16, 14));
	private static final VoxelShape BIG = Shapes.or(STEM, Block.box(0, 12, 0, 16, 16, 16));

	public PulseTreeBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(SHAPE, Shape.STEM));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SHAPE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(SHAPE)) {
			case STEM -> STEM;
			case HEAD_SMALL -> SMALL;
			case HEAD_MEDIUM -> MEDIUM;
			case HEAD_BIG -> BIG;
		};
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return below.is(this) || below.is(Eden.PULSE_LOG) || below.is(Eden.MYCELIUM) || below.is(BlockTags.DIRT);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
		BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
			ticks.scheduleTick(pos, this, 1);
		}
		return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.canSurvive(level, pos)) {
			level.destroyBlock(pos, true);
		}
	}
}
