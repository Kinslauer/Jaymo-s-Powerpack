package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.MobCombat;
import com.jaymoskingdoms.MobShields;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands a humanoid's melee attacks to {@link MobCombat} (Better Combat's weapon moves) when it fights that way; every
 * other mob, and every mob without Better Combat, keeps vanilla's attack. (0.7.105) First, a mob with a shield may hold
 * it up instead of striking ({@link MobShields}).
 */
@Mixin(MeleeAttackGoal.class)
abstract class MeleeAttackGoalMixin {
	@Shadow
	@Final
	protected PathfinderMob mob;

	@Inject(method = "checkAndPerformAttack", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$weaponMoves(LivingEntity target, CallbackInfo ci) {
		// (0.7.105) a shield held up: no blow until it comes down (MobShields)
		if (MobShields.guard(this.mob, target)) {
			ci.cancel();
			return;
		}
		if (MobCombat.ENABLED && MobCombat.handles(this.mob)) {
			MobCombat.attackTick(this.mob, target);
			ci.cancel();
		}
	}
}
