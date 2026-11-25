package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Blows;
import com.jaymoskingdoms.MobCombat;
import com.jaymoskingdoms.MobShields;
import com.jaymoskingdoms.NpcDodge;
import net.minecraft.world.InteractionHand;
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
 * <p>
 * Fighting AI patch 3 ({@link Blows}): the game's blow, for a humanoid without Better Combat, comes at once just after a blow
 * taken on its shield (sh-counter), never sooner than its weapon's full charge, maybe from a jump's fall (ml-crit); it lands from
 * the game's own reach. After its blow it steps back toward its weapon's reach, never past where it can strike from (ml-reach;
 * every fighter the goal runs).
 * <p>
 * Fighting AI patch 7 (st-dodge): a bandit or assassin may roll out of the blow as it comes due ({@link NpcDodge#incoming}); the blow
 * rolled out of is spent ({@link NpcDodge#dodged}).
 */
@Mixin(MeleeAttackGoal.class)
abstract class MeleeAttackGoalMixin {
	@Shadow
	@Final
	protected PathfinderMob mob;

	@Shadow
	private int ticksUntilNextAttack;

	@Shadow
	protected abstract void resetAttackCooldown();

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
			return;
		}
		// (sh-counter) a blow just taken on its shield: its own comes at once
		if (MobShields.countering(this.mob)) {
			this.ticksUntilNextAttack = 0;
		}
		// (ml-crit) never sooner than a full charge, and a heavy weapon's blow may wait for the fall of a jump
		if (this.ticksUntilNextAttack <= 0 && Blows.holds(this.mob, target)) {
			ci.cancel();
			return;
		}
		// (st-dodge) a bandit or assassin it swings at may read the blow coming and roll out of it
		if (this.ticksUntilNextAttack <= NpcDodge.ROLL_LEAD && this.mob.isWithinMeleeAttackRange(target) && this.mob.getSensing().hasLineOfSight(target)) {
			NpcDodge.incoming(target, this.mob, this.ticksUntilNextAttack);
		}
		// the blow it rolled out of goes by, swung at the air, and is spent: the next waits its full recovery
		if (this.ticksUntilNextAttack <= 0 && NpcDodge.dodged(target, this.mob)) {
			this.resetAttackCooldown();
			this.mob.swingForAttack(InteractionHand.MAIN_HAND);
			ci.cancel();
		}
	}

	/** (ml-reach) After its own tick: just after a blow, stepping back to its weapon's reach. */
	@Inject(method = "tick", at = @At("TAIL"))
	private void jaymoskingdoms$spacing(CallbackInfo ci) {
		LivingEntity target = this.mob.getTarget();
		if (target != null) {
			Blows.space(this.mob, target);
		}
	}
}
