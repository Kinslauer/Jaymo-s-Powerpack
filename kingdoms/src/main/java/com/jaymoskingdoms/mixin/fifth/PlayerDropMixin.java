package com.jaymoskingdoms.mixin.fifth;

import com.jaymoskingdoms.RelicCurses;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A thing under the clinging curse that its bonded holder tries to drop is back in their pack (not when they die). */
@Mixin(ServerPlayer.class)
abstract class PlayerDropMixin {
	@Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
		at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$cling(ItemStack stack, boolean randomly, Prediction prediction, CallbackInfoReturnable<ItemEntity> cir) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (self.isAlive() && RelicCurses.clings(self, stack)) {
			if (!self.getInventory().add(stack)) {
				self.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
			}
			cir.setReturnValue(null);
		}
	}
}
