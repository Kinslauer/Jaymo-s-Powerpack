package com.jaymospowers.mixin;

import com.jaymospowers.core.Dismember;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A knock on one of the undead may take a limb off it in place of throwing it back ({@link Dismember#knockedApart}). */
@Mixin(LivingEntity.class)
abstract class KnockApartMixin {
	@Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$knockApart(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect,
		CallbackInfo ci) {
		if (Dismember.knockedApart((LivingEntity)(Object)this, power, xd, zd, source)) {
			ci.cancel();
		}
	}
}
