package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.MobAmmo;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A pillager out of bolts stops aiming its crossbow (and clubs with it instead: {@link MobAmmo}). */
@Mixin(RangedCrossbowAttackGoal.class)
abstract class CrossbowGoalAmmoMixin {
	@Shadow
	@Final
	private Monster mob;

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$bolts(CallbackInfoReturnable<Boolean> cir) {
		if (!this.mob.level().isClientSide() && !MobAmmo.has(this.mob, MobAmmo.Kind.ARROWS)) {
			cir.setReturnValue(false);
		}
	}
}
