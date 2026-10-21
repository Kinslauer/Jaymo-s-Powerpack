package com.jaymospowers.aether;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Woven skyroot sticks: a nest base, a block whose top sits {@link #HEIGHT} pixels up (a moa's nest stands on it). */
public class WovenSticksBlock extends Block {
	// ---- tuning ----
	/** Height in pixels (a carpet is 1, a slab 8). */
	public static final double HEIGHT = 8.0;

	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, HEIGHT, 16.0);

	public WovenSticksBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}
}
