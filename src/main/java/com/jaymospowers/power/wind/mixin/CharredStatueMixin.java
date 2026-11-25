package com.jaymospowers.power.wind.mixin;

import com.jaymospowers.power.wind.WindElectro;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A creature a Thunderhead's lightning killed stands as a charcoal statue, then crumbles (WindElectro#statueTick), instead of toppling. */
@Mixin(LivingEntity.class)
abstract class CharredStatueMixin {
	@Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$charredStatue(CallbackInfo ci) {
		if (WindElectro.statueTick((LivingEntity)(Object)this)) {
			ci.cancel();
		}
	}
}
