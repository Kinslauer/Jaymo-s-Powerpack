package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.ClassPerks;
import com.jaymoskingdoms.CombatArts;
import com.jaymoskingdoms.Groups;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.HiddenBlade;
import com.jaymoskingdoms.Senses;
import com.jaymoskingdoms.VillagerHunters;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * (0.6.0) What a class's weapons do to whatever they hit ({@link ClassPerks#dealt}): an Archer's arrows hit harder. (0.6.5) Then
 * the Combat Arts' Momentum ({@link CombatArts#dealt}). (0.7.106) Then the heritages' own
 * ({@link Heritage#dealt}): a Dragonblood's fire halved, a Dawnborn against the undead. (0.7.102) Last, the hidden blade:
 * it jams in unskilled hands, and stabs a Crippled foe for ten times the blow ({@link HiddenBlade#dealt}).
 * (0.7.111) And if it left the blow alone, a sneak attack on a monster that doesn't know you're there ({@link Senses#dealt}).
 * (0.7.184) Then a player's group ({@link Groups#dealt}): a groupmate's blow lands at a quarter.
 * (0.7.217) Last, a monster's blow on a villager lands softer, and a golem's kills a creeper ({@link VillagerHunters#dealt}).
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityHurtMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymoskingdoms$classDamage(float amount, ServerLevel level, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		float dealt = Heritage.dealt(self, source, CombatArts.dealt(self, source, ClassPerks.dealt(self, source, amount)));
		float stabbed = HiddenBlade.dealt(self, source, dealt);
		// (0.7.184) and a groupmate's blow cut down, anyone else's eased by groupmates near
		return VillagerHunters.dealt(self, source, Groups.dealt(self, source, stabbed != dealt ? stabbed : Senses.dealt(self, source, dealt)));
	}
}
