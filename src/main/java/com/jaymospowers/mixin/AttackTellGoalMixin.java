package com.jaymospowers.mixin;

import com.jaymospowers.combat.AttackTells;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** (0.17.64) A goal-driven mob's melee blow winds up with a glint first (see AttackTells). */
@Mixin(MeleeAttackGoal.class)
abstract class AttackTellGoalMixin {
	@WrapOperation(method = "checkAndPerformAttack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/PathfinderMob;doHurtTarget(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Z"))
	private boolean jaymospowers$windUp(PathfinderMob mob, ServerLevel level, Entity target, Operation<Boolean> original) {
		return AttackTells.windUp(mob, level, target);
	}
}
