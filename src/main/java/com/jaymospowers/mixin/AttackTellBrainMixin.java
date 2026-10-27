package com.jaymospowers.mixin;

import com.jaymospowers.combat.AttackTells;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** (0.17.67) A brain-driven mob's melee blow (piglins, hoglins, zoglins) winds up with a glint first (see AttackTells). */
@Mixin(MeleeAttack.class)
abstract class AttackTellBrainMixin {
	@WrapOperation(method = "lambda$create$3", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/Mob;doHurtTarget(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Z"))
	private static boolean jaymospowers$windUp(Mob mob, ServerLevel level, Entity target, Operation<Boolean> original) {
		return AttackTells.windUp(mob, level, target);
	}
}
