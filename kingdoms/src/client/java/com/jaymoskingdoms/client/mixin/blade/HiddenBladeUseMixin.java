package com.jaymoskingdoms.client.mixin.blade;

import com.jaymoskingdoms.client.HiddenBladeClient;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.113) Use with an empty hand springs that wrist's hidden blade: each hand in turn, once neither an entity nor a block
 * took the click (where vanilla would use the held item, and does nothing for an empty hand).
 */
@Mixin(Minecraft.class)
abstract class HiddenBladeUseMixin {
	@Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z", ordinal = 1), cancellable = true)
	private void jaymoskingdoms$springBlade(CallbackInfo ci, @Local InteractionHand hand, @Local ItemStack heldItem) {
		if (heldItem.isEmpty() && HiddenBladeClient.use((Minecraft)(Object)this, hand)) {
			ci.cancel();
		}
	}
}
