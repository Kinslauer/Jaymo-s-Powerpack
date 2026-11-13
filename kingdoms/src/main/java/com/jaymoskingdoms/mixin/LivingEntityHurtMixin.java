package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Careers;
import com.jaymoskingdoms.ClassPerks;
import com.jaymoskingdoms.CombatArts;
import com.jaymoskingdoms.Flaws;
import com.jaymoskingdoms.Groups;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.HiddenBlade;
import com.jaymoskingdoms.Senses;
import com.jaymoskingdoms.VillagerHunters;
import com.jaymoskingdoms.Valor;
import com.jaymoskingdoms.ValorFolk;
import com.jaymoskingdoms.Will;
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
 * (0.7.640) First of all, a wooden skewer into a vampire bites like a stake ({@link com.jaymoskingdoms.Vampires#staked}),
 * then a mob vampire's torch, burning-arrow and silver weaknesses ({@link com.jaymoskingdoms.Vampires#weakness}).
 * Before all of it, fifth edition's upper hand and bad footing ({@link com.jaymoskingdoms.UpperHand}) and a moving body's
 * save against blasts ({@link com.jaymoskingdoms.Saves#dealt}). Then a held or sleeping body's crit ({@link com.jaymoskingdoms.HeldFast}),
 * a reckless blow, a Thief's sneak attack, a poisoned blade, and a foe caught unawares freezing ({@link com.jaymoskingdoms.Surprise}).
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityHurtMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float jaymoskingdoms$classDamage(float amount, ServerLevel level, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;
		// (0.7.220) and a Master Hunter's on beasts
		float dealt = com.jaymoskingdoms.HunterPack.dealt(self, source, Careers.dealt(self, source, Flaws.dealt(self, source, com.jaymoskingdoms.Blessings.dealt(self, source, Heritage.dealt(self, source, Valor.dealt(self, source, Will.dealt(self, source, CombatArts.dealt(self, source, com.jaymoskingdoms.BruteRage.dealt(self, source, ClassPerks.dealt(self, source, com.jaymoskingdoms.Vampires.weakness(self, source, com.jaymoskingdoms.Vampires.staked(self, source, com.jaymoskingdoms.Concentration.dealt(self, source, com.jaymoskingdoms.Saves.dealt(self, source, com.jaymoskingdoms.Surprise.dealt(self, source, com.jaymoskingdoms.Poisons.dealt(self, source, com.jaymoskingdoms.ThiefSneak.dealt(self, source, com.jaymoskingdoms.RecklessBlows.dealt(self, source, com.jaymoskingdoms.HeldFast.dealt(self, source, com.jaymoskingdoms.UpperHand.dealt(self, source, com.jaymoskingdoms.UnderWater.dealt(self, source, com.jaymoskingdoms.DamageKinds.dealt(self, source, amount))))))))))))))))))))));
		float stabbed = HiddenBlade.dealt(self, source, dealt);
		// (0.7.184) and a groupmate's blow cut down, anyone else's eased by groupmates near
		// (0.7.361) Valor's: Reinforcement, Armament, King's coating, after Will's; last, a spar stops short of harm
		// (0.7.431) last, a tribe's captain takes it as his traits have it (Captains)
		// (0.7.433) a tribe's king, while his captains stand (SwornCaptains)
		// (0.7.492) last, a kneeling boss takes a riposte (BossPosture)
		// (and Watched, the gaze built from hiding, spends itself on the blow: RogueGambits)
		// (pawn picks) a companion who knows the kind hits it harder (FoeLore); (last of all, a feat's say and a path's)
		return com.jaymoskingdoms.Paths.dealt(self, source, com.jaymoskingdoms.Feats.dealt(self, source, com.jaymoskingdoms.CampSuppers.dealt(self, source, com.jaymoskingdoms.FoeLore.dealt(self, source, com.jaymoskingdoms.SettlerKit.dealt(self, source, com.jaymoskingdoms.RogueGambits.dealt(self, source, com.jaymoskingdoms.BossPosture.dealt(self, source, com.jaymoskingdoms.SwornCaptains.guarded(self, source, com.jaymoskingdoms.Captains.dealt(self, source, ValorFolk.spar(self, source, VillagerHunters.dealt(self, source, Groups.dealt(self, source, stabbed != dealt ? stabbed : Senses.dealt(self, source, dealt)))))))))))));
	}
}
