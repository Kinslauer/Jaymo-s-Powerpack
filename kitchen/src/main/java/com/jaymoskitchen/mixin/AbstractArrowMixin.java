package com.jaymoskitchen.mixin;

import com.jaymoskitchen.archery.LegShots;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Right after an arrow hurts something (where tipped arrows apply their potion), check for a leg shot. */
@Mixin(AbstractArrow.class)
abstract class AbstractArrowMixin {
	@Inject(
		method = "onHitEntity",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/projectile/arrow/AbstractArrow;doPostHurtEffects(Lnet/minecraft/world/entity/LivingEntity;)V"))
	private void jaymoskitchen$legShot(EntityHitResult hitResult, CallbackInfo ci) {
		if (hitResult.getEntity() instanceof LivingEntity target) {
			LegShots.onArrowHit((AbstractArrow) (Object) this, target, hitResult.getLocation());
		}
	}
}
