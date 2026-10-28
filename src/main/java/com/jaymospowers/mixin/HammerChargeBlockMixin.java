package com.jaymospowers.mixin;

import com.jaymospowers.armory.HammerSlam;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.17.98) A warhammer carries the weapon guard ({@code combat/Guard}) for while its smash cools down; charging the
 * smash isn't a block ({@link HammerSlam#charging}).
 */
@Mixin(LivingEntity.class)
abstract class HammerChargeBlockMixin {
	@Inject(method = "getItemBlockingWith", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noBlockWhileCharging(CallbackInfoReturnable<ItemStack> cir) {
		if (HammerSlam.charging((LivingEntity)(Object)this)) {
			cir.setReturnValue(null);
		}
	}
}
