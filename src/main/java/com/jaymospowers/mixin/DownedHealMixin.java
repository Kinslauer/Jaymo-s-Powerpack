package com.jaymospowers.mixin;

import com.jaymospowers.core.wounds.Downed;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.15.28) The downed don't heal, from anything: no meal (Kitchen's stomach), no power, no potion. Andy (2026-10-21):
 * "you shouldnt be healing to full health on the ground with no assistance". Only being brought round, or the smelling
 * salts, gets them up ({@link Downed}); treatment works once they are.
 */
@Mixin(LivingEntity.class)
abstract class DownedHealMixin {
	@Inject(method = "heal", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noHealWhileDown(float amount, CallbackInfo ci) {
		if (Downed.isDowned((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
