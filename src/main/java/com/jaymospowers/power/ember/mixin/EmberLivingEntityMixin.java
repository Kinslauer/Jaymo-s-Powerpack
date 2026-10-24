package com.jaymospowers.power.ember.mixin;

import com.jaymospowers.power.ember.FlameAvatar;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Flame Avatar's flame takes a hit before the hearts do (see FlameAvatar#absorb). */
@Mixin(LivingEntity.class)
abstract class EmberLivingEntityMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$flame(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		if (FlameAvatar.absorb((LivingEntity)(Object)this, level, source, damage)) {
			cir.setReturnValue(false);
		}
	}
}
