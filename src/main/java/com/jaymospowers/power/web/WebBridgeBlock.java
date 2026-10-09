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
 * One plank of a webline (see {@link WebBridges}), as tall as the line is where it crosses this block ({@link #HEIGHT},
 * sixteenths), so a sloping line climbs in small steps you can walk up. A plain web's planks are unseen (the line itself
 * is drawn); a symbiote's show, a mat of black rope ({@link #ROPE}, the suit) or of tendrils ({@link #TENDRIL}, Living
 * Armor).
 */
public class WebBridgeBlock extends Block {
	public static final int WEB = 0, ROPE = 1, TENDRIL = 2;
	public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 1, 16);
	public static final IntegerProperty STYLE = IntegerProperty.create("style", WEB, TENDRIL);
	private static final VoxelShape[] SHAPES = new VoxelShape[17];

	static {
		for (int h = 1; h <= 16; h++) {
			SHAPES[h] = Block.box(0.0, Math.max(0.0, h - 2.0), 0.0, 16.0, h, 16.0);
		}
	}

	public WebBridgeBlock(final Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(HEIGHT, 2).setValue(STYLE, WEB));
	}

	@Override
	protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(HEIGHT, STYLE);
	}

	/** A plain web's plank is unseen: the webline itself is drawn between its ends (see WebLineRenderer). */
	@Override
	protected net.minecraft.world.level.block.RenderShape getRenderShape(final BlockState state) {
		return state.getValue(STYLE) == WEB ? net.minecraft.world.level.block.RenderShape.INVISIBLE : net.minecraft.world.level.block.RenderShape.MODEL;
	}

	@Override
	protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
		return SHAPES[state.getValue(HEIGHT)];
	}
}
