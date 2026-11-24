package com.jaymospowers.mixin;

import com.jaymospowers.core.wounds.Downed;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blows don't throw the downed about. Andy (2026-11-22): "this should change how fights end, as of now i get tossed
 * around for 30s". A blast still moves a body (it pushes without a knockback).
 */
@Mixin(LivingEntity.class)
abstract class DownedStillMixin {
	@Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$downedStill(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect,
		CallbackInfo ci) {
		if (Downed.isDowned((LivingEntity)(Object)this)) {
			ci.cancel();
		}
	}
}
