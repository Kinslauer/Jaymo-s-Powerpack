package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.CaptiveConduct;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (Andy, 2026-11-19) A captor can't be set on a captive keeping their place ({@link CaptiveConduct#spares}), whoever sets
 * them on: a captain's bodyguard taking up his boss's fight, a rally, a comrade's call. Their own targeting already asks
 * {@code canAttack} ({@link ThrallTargetMixin}); those calls didn't, so the camp crowded a captive who had just woken in
 * chains, set on them and let go again over and over.
 */
@Mixin(Mob.class)
abstract class CaptorHoldMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$holdOffCaptive(@Nullable LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob)(Object)this;
		if (target != null && !self.level().isClientSide() && CaptiveConduct.spares(self, target)) {
			ci.cancel();
		}
	}
}
