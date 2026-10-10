package com.jaymoskitchen.mixin;

import com.jaymoskitchen.table.CraftingTableEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The crafting table keeps its grid ({@link CraftingTableEntity}), and turns away a second player. */
@Mixin(CraftingTableBlock.class)
abstract class CraftingTableBlockMixin implements EntityBlock {
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CraftingTableEntity(pos, state);
	}

	@Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$oneAtATime(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
		CallbackInfoReturnable<InteractionResult> cir) {
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CraftingTableEntity table && table.busyFor(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.table.busy"));
			cir.setReturnValue(InteractionResult.SUCCESS);
		}
	}
}
