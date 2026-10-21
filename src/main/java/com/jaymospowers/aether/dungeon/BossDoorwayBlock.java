package com.jaymospowers.aether.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The boss room's door. Open (how the structure places it) it is nothing: no collision, nothing drawn, you walk through.
 * Closed, it is a wall that looks like locked sentry bricks and cannot be broken. The Slider closes every doorway of its
 * room when the fight starts and opens them again when it dies (or when everyone has left).
 */
public class BossDoorwayBlock extends Block {
	public static final BooleanProperty CLOSED = BooleanProperty.create("closed");

	public BossDoorwayBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(CLOSED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(CLOSED);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return state.getValue(CLOSED) ? RenderShape.MODEL : RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(CLOSED) ? Shapes.block() : Shapes.empty();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(CLOSED) ? Shapes.block() : Shapes.empty();
	}

	@Override
	protected boolean propagatesSkylightDown(BlockState state) {
		return !state.getValue(CLOSED);
	}
}
