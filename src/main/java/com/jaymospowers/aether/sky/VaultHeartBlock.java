package com.jaymospowers.aether.sky;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** (0.20.96) The keystone set in a watched vault's floor: what moves its chest and crystals when nobody looks ({@link VaultHeartEntity}). */
public class VaultHeartBlock extends BaseEntityBlock {
	public VaultHeartBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new VaultHeartEntity(pos, state);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() || type != SkyBlocks.VAULT_HEART_ENTITY ? null
			: (BlockEntityTicker<T>) (BlockEntityTicker<VaultHeartEntity>) VaultHeartEntity::serverTick;
	}
}
