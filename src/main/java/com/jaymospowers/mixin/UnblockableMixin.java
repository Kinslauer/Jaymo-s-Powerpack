package com.jaymospowers.mixin;

import com.jaymospowers.combat.Guard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A yellow blow ({@link Guard#UNBLOCKABLE_TAG}) can't be blocked: a raised guard or shield stops none of it and is knocked
 * aside ({@link Guard#knockAside}). A parry has already turned it away by now (Guard's damage hook), so what gets here has
 * landed.
 */
@Mixin(LivingEntity.class)
abstract class UnblockableMixin {
	@Inject(method = "applyItemBlocking", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$unblockable(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		if (Guard.knockAside(level, (LivingEntity) (Object) this, source)) {
			cir.setReturnValue(0.0F);
		}
	}
}
