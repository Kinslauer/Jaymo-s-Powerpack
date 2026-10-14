package com.jaymospowers.power.smoke.mixin;

import com.jaymospowers.power.smoke.SmokeField;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mob can't take a target it can't see for smoke (or one gone to vapour), whatever its own AI wants. */
@Mixin(Mob.class)
abstract class SmokeTargetMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$smokeHides(@Nullable LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (target != null && !self.level().isClientSide() && SmokeField.hidden(self, target)) {
			ci.cancel();
		}
	}
}
