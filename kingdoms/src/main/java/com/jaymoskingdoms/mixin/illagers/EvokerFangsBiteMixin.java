package com.jaymoskingdoms.mixin.illagers;

import com.jaymoskingdoms.illagers.InvokerFangs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** An invoker's fangs bite their own way (InvokerFangs#bite): vanilla's bite is private, so it's handed over here. */
@Mixin(EvokerFangs.class)
abstract class EvokerFangsBiteMixin {
	@Inject(method = "dealDamageTo", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$invokerBite(LivingEntity target, CallbackInfo ci) {
		if ((Object)this instanceof InvokerFangs fangs) {
			fangs.bite(target);
			ci.cancel();
		}
	}
}
