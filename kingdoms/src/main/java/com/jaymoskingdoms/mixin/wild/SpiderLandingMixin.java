package com.jaymoskingdoms.mixin.wild;

import com.jaymoskingdoms.wild.SpiderSquash;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A landing (the game reckons the fall as anything comes down, however short) squashes the spiders under the lander's
 * feet ({@link SpiderSquash#landed}): a jump onto a bigger one.
 */
@Mixin(LivingEntity.class)
abstract class SpiderLandingMixin {
	@Inject(method = "causeFallDamage", at = @At("HEAD"))
	private void jaymoskingdoms$squashUnderfoot(double fallDistance, float damageModifier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().isClientSide()) {
			SpiderSquash.landed(self, fallDistance);
		}
	}
}
