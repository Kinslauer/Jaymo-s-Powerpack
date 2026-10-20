package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.ClassPerks;
import com.jaymoskingdoms.CombatArts;
import com.jaymoskingdoms.HiddenBlade;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * (0.6.0) What a class's weapons do to whatever they hit ({@link ClassPerks#dealt}): an Archer's arrows hit harder. (0.6.5) Then
 * the Combat Arts' Momentum and Executioner ({@link CombatArts#dealt}). (0.7.98) Last, the hidden blade: it jams in unskilled
 * hands, and stabs a Crippled foe for ten times the blow ({@link HiddenBlade#dealt}).
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityHurtMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymoskingdoms$classDamage(float amount, ServerLevel level, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		return HiddenBlade.dealt(self, source, CombatArts.dealt(self, source, ClassPerks.dealt(source, amount)));
	}
}
