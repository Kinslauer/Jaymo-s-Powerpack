package com.jaymoskingdoms.mixin.equestrian;

import com.jaymoskingdoms.equestrian.Riders;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Rider rides bareback: a tamed horse, camel or llama under one goes as if saddled ({@link Riders#bareback}). */
@Mixin(Mob.class)
abstract class MobSaddleMixin {
	@Inject(method = "isSaddled", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$bareback(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && Riders.bareback((Mob)(Object)this)) {
			cir.setReturnValue(true);
		}
	}
}
