package com.jaymospowers.mixin;

import com.jaymospowers.combat.AttackTells;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.17.67) A brain-driven mob's melee blow (piglins, hoglins, zoglins) winds up with a glint first (see AttackTells).
 * It starts only from close in ({@link AttackTells#closeEnough}), and its arm swings when the blow falls.
 */
@Mixin(MeleeAttack.class)
abstract class AttackTellBrainMixin {
	@WrapOperation(method = "lambda$create$3", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Mob;isWithinMeleeAttackRange(Lnet/minecraft/world/entity/LivingEntity;)Z"))
	private static boolean jaymospowers$closeEnough(Mob mob, LivingEntity target, Operation<Boolean> original) {
		return original.call(mob, target) && AttackTells.closeEnough(mob, target);
	}

	@WrapOperation(method = "lambda$create$3", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Mob;swingForAttack(Lnet/minecraft/world/InteractionHand;)V"))
	private static void jaymospowers$swingOnBlow(Mob mob, InteractionHand hand, Operation<Void> original) {
		// AttackTells swings it when the blow falls
	}

	@WrapOperation(method = "lambda$create$3", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Mob;doHurtTarget(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Z"))
	private static boolean jaymospowers$windUp(Mob mob, ServerLevel level, Entity target, Operation<Boolean> original) {
		return AttackTells.windUp(mob, level, target);
	}
}
