package com.jaymospowers.power.web;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One plank of a webline (see {@link WebBridges}): unseen (the line itself is drawn), and as tall as the line is where
 * it crosses this block ({@link #HEIGHT}, sixteenths), so a sloping line climbs in small steps you can walk up.
 */
public class WebBridgeBlock extends Block {
	public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 1, 16);
	private static final VoxelShape[] SHAPES = new VoxelShape[17];

	static {
		for (int h = 1; h <= 16; h++) {
			SHAPES[h] = Block.box(0.0, Math.max(0.0, h - 2.0), 0.0, 16.0, h, 16.0);
		}
	}

	public WebBridgeBlock(final Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(HEIGHT, 2));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HEIGHT);
	}

	/** Unseen: the webline itself is drawn as a line between its ends (see WebLineRenderer). */
	@Override
	protected net.minecraft.world.level.block.RenderShape getRenderShape(final BlockState state) {
		return net.minecraft.world.level.block.RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPES[state.getValue(HEIGHT)];
	}
}
