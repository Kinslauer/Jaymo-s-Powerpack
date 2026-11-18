package com.jaymospowers.mixin;

import com.jaymospowers.fighting.HitKinds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Immunity after a hit is per kind of damage (see HitKinds): before a hit is weighed against the cooldown, a fresh kind clears it. */
@Mixin(LivingEntity.class)
abstract class LivingEntityHitKindsMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void jaymospowers$hitKinds(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		HitKinds.before((LivingEntity) (Object) this, source, amount, level.getGameTime());
	}
}
