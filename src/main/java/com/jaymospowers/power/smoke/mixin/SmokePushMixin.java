package com.jaymospowers.power.smoke.mixin;

import com.jaymospowers.power.smoke.SmokeVapour;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Wholly smoke: creatures pass through, neither pushing nor pushed. */
@Mixin(LivingEntity.class)
abstract class SmokePushMixin {
	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$smokePush(CallbackInfoReturnable<Boolean> cir) {
		if ((Object)this instanceof Player player && SmokeVapour.wholly(player)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "pushEntities", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$smokePushOthers(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if ((Object)this instanceof Player player && SmokeVapour.wholly(player)) {
			ci.cancel();
		}
	}
}
