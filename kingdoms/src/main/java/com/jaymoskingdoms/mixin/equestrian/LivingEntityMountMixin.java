package com.jaymoskingdoms.mixin.equestrian;

import com.jaymoskingdoms.equestrian.Mounted;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.305) A ridden horse steps up higher ({@link Mounted#step}). */
@Mixin(LivingEntity.class)
abstract class LivingEntityMountMixin {
	@Inject(method = "maxUpStep", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$step(CallbackInfoReturnable<Float> cir) {
		float step = Mounted.step((LivingEntity)(Object)this, cir.getReturnValueF());
		if (step != cir.getReturnValueF()) {
			cir.setReturnValue(step);
		}
	}
}
