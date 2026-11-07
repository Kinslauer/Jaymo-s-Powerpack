package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.7.462) The infirmary cot: a low bed (two blocks, like any bed, but a hand lower) of planks and wool. It is a vanilla
 * {@link BedBlock} in everything but looks: it sleeps and rests ({@link BedRest}) and sets the spawn as a bed does, so the
 * sleep code needs nothing new. It has no colour and no block entity (a cot is drawn by its model, not the bed renderer).
 * Whoever lies on one for {@link Healing#COT_TICKS} where a healer works within {@link Healing#HEALER_NEAR} blocks, or a
 * brewing stand within {@link Healing#STAND_NEAR}, is treated as a healer would ({@link Healing#treat}). See
 * {@link Healing#tick}.
 */
public final class InfirmaryCot extends BedBlock {
	/** (0.7.462) How high the cot stands (sixteenths of a block); vanilla beds stand 9. */
	static final int HEIGHT = 6;
	private static final VoxelShape SHAPE = net.minecraft.world.level.block.Block.box(0.0, 0.0, 0.0, 16.0, HEIGHT, 16.0);

	public InfirmaryCot(BlockBehaviour.Properties properties) {
		super(DyeColor.WHITE, properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	/** Drawn from its block model, not the bed renderer. */
	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}
}
