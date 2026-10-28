package com.jaymoskingdoms.mixin.equestrian;

import com.jaymoskingdoms.equestrian.Mounted;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.305) Our horses take a second rider, and swim with their riders instead of throwing them in deep water ({@link Mounted}). */
@Mixin(Entity.class)
abstract class EntityMountMixin {
	@Inject(method = "canAddPassenger", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$pillion(Entity passenger, CallbackInfoReturnable<Boolean> cir) {
		if (Mounted.pillion((Entity)(Object)this, passenger)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "dismountsUnderwater", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$swims(CallbackInfoReturnable<Boolean> cir) {
		if (Mounted.swims((Entity)(Object)this)) {
			cir.setReturnValue(false);
		}
	}
}
