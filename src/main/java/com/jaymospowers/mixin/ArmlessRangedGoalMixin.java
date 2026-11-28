package com.jaymospowers.mixin;

import com.jaymospowers.core.Dismember;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A mob missing both arms cannot throw potions or tridents or fire ranged hand attacks ({@link Dismember#armless}). */
@Mixin(RangedAttackGoal.class)
abstract class ArmlessRangedGoalMixin {
	@Shadow
	@Final
	private Mob mob;

	@Inject(method = {"canUse", "canContinueToUse"}, at = @At("HEAD"), cancellable = true)
	private void jaymospowers$noArmsForRanged(CallbackInfoReturnable<Boolean> cir) {
		if (Dismember.armless(this.mob)) {
			cir.setReturnValue(false);
		}
	}
}
