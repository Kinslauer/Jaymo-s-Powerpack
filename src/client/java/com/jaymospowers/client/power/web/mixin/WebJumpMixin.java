package com.jaymospowers.client.power.web.mixin;

import com.jaymospowers.client.power.web.WebSwing;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wearing the suit, Jump coils a charged leap instead of jumping straight away (see WebSwing#tickChargeJump). */
@Mixin(LivingEntity.class)
abstract class WebJumpMixin {
	@Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$webChargeJump(CallbackInfo ci) {
		if ((Object)this instanceof LocalPlayer player && WebSwing.holdingCharge(player)) {
			ci.cancel();
		}
	}
}
