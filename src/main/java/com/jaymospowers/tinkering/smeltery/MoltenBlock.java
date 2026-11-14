package com.jaymospowers.tinkering.smeltery;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** The block of a {@link MoltenEntity}: ticks it, and an empty hand reads what's in it. */
public abstract class MoltenBlock extends Block implements EntityBlock {
	protected MoltenBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : (tickLevel, pos, tickState, entity) -> {
			if (entity instanceof MoltenEntity molten) {
				MoltenEntity.serverTick(tickLevel, pos, tickState, molten);
			}
		};
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MoltenEntity entity) {
			player.sendOverlayMessage(readout(entity));
		}
		return InteractionResult.SUCCESS;
	}

	/** "Molten Iron: 2 ingots, 30 mB", every liquid in it, or that it's empty. */
	static Component readout(MoltenEntity entity) {
		Component line = null;
		for (Tank.Layer layer : entity.tank().layers()) {
			Component one = Component.translatable("message.jaymospowers.molten.holds", layer.molten.displayName(),
				Molten.amount(layer.amount, layer.molten.metal()));
			line = line == null ? one : Component.translatable("molten.jaymospowers.and", line, one);
		}
		return line == null ? Component.translatable("message.jaymospowers.molten.empty") : line;
	}
}
