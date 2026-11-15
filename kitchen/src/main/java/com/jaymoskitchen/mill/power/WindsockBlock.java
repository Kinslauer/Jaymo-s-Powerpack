package com.jaymoskitchen.mill.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A windsock (GT New Horizons picks, patch 8): a pole with a striped cloth sock on a swivel at its top. The sock streams
 * downwind and stands out straighter and flutters faster the harder it blows ({@link WindsockEntity}); in a calm, or
 * sheltered from the sky, it hangs limp. It's how a miller reads where to set the sails and when to take them down.
 * The pole is the block's model; the sock is drawn turning by its renderer.
 */
public class WindsockBlock extends BaseEntityBlock {
	private static final VoxelShape SHAPE = Block.box(7, 0, 7, 9, 16, 9);

	public WindsockBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new WindsockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? createTickerHelper(type, Power.WINDSOCK_ENTITY, WindsockEntity::clientTick)
			: createTickerHelper(type, Power.WINDSOCK_ENTITY, WindsockEntity::serverTick);
	}
}
