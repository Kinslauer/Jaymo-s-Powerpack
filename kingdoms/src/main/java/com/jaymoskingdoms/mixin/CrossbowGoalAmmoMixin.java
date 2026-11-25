package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.MobAmmo;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Final;
import com.jaymoskingdoms.WeaponSense;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A pillager out of bolts stops aiming its crossbow (and clubs with it instead: {@link MobAmmo}). And it stops walking up on its foe
 * at {@link WeaponSense#CROSSBOW_STOP} blocks, not 8, so it shoots from about the crossbow's own distance band and not from point-blank;
 * what is left (too close, too far) is the band's ({@link WeaponSense}).
 */
@Mixin(RangedCrossbowAttackGoal.class)
abstract class CrossbowGoalAmmoMixin {
	@Shadow
	@Final
	private Monster mob;

	@Shadow
	@Final
	@Mutable
	private float attackRadiusSqr;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void jaymoskingdoms$band(CallbackInfo ci) {
		this.attackRadiusSqr = WeaponSense.CROSSBOW_STOP * WeaponSense.CROSSBOW_STOP;
	}

	@Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$bolts(CallbackInfoReturnable<Boolean> cir) {
		if (!this.mob.level().isClientSide() && !MobAmmo.has(this.mob, MobAmmo.Kind.ARROWS)) {
			cir.setReturnValue(false);
		}
	}
}
