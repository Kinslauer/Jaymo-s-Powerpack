package com.jaymospowers.mixin;

import com.jaymospowers.combat.AttackTells;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.17.67) A goal-driven mob's melee blow winds up with a glint first (see AttackTells). It starts only from close in
 * ({@link AttackTells#closeEnough}), and its arm swings when the blow falls, not with the glint.
 */
@Mixin(MeleeAttackGoal.class)
abstract class AttackTellGoalMixin {
	@Shadow
	@Final
	protected PathfinderMob mob;

	@ModifyReturnValue(method = "canPerformAttack", at = @At("RETURN"))
	private boolean jaymospowers$closeEnough(boolean can, LivingEntity target) {
		return can && AttackTells.closeEnough(this.mob, target);
	}

	@WrapOperation(method = "checkAndPerformAttack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/PathfinderMob;swingForAttack(Lnet/minecraft/world/InteractionHand;)V"))
	private void jaymospowers$swingOnBlow(PathfinderMob mob, InteractionHand hand, Operation<Void> original) {
		// AttackTells swings it when the blow falls
	}

	@WrapOperation(method = "checkAndPerformAttack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/PathfinderMob;doHurtTarget(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean jaymospowers$windUp(PathfinderMob mob, ServerLevel level, Entity target, Operation<Boolean> original) {
		return AttackTells.windUp(mob, level, target);
	}
}
