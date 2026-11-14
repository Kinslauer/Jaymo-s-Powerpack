package com.jaymoskingdoms.reaped;

import com.jaymoskingdoms.ReapedHost;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.level.Level;

/**
 * A reaped evoker ({@link Reaped}): it still casts its fangs, but where a living evoker calls vexes it lifts the bodies
 * lying near it back into the fight ({@link ReapedHost#raise}). Without Jaymo's Kitchen's corpses it keeps its vexes.
 */
public class ReapedEvoker extends Evoker implements Reaped {
	public ReapedEvoker(EntityType<? extends ReapedEvoker> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		if (ReapedHost.raises()) {
			this.goalSelector.removeAllGoals(goal -> goal.getClass().getName().endsWith("EvokerSummonSpellGoal"));
			this.goalSelector.addGoal(4, new RaiseGoal());
		}
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, AbstractIllager.class, 10, true, false, (target, level) -> Reaped.prey(target)));
	}

	@Override
	protected boolean considersEntityAsAlly(Entity other) {
		return other == this || Reaped.ally(this, other)
			|| other instanceof net.minecraft.world.entity.monster.Vex vex && vex.getRootOwner() instanceof Reaped;
	}

	@Override
	public boolean canAttack(LivingEntity target) {
		return !Reaped.spares(this, target) && super.canAttack(target);
	}

	/** Its arms hang: they never cross as a living illager's do. */
	@Override
	public IllagerArmPose getArmPose() {
		IllagerArmPose pose = super.getArmPose();
		return pose == IllagerArmPose.CROSSED ? IllagerArmPose.NEUTRAL : pose;
	}

	private class RaiseGoal extends SpellcasterUseSpellGoal {
		@Override
		public boolean canUse() {
			return super.canUse() && ReapedHost.bodies(ReapedEvoker.this);
		}

		@Override
		protected int getCastingTime() {
			return 60;
		}

		@Override
		protected int getCastingInterval() {
			return 240;
		}

		@Override
		protected void performSpellCasting() {
			ReapedHost.raise(ReapedEvoker.this);
		}

		@Override
		protected SoundEvent getSpellPrepareSound() {
			return SoundEvents.EVOKER_PREPARE_SUMMON;
		}

		@Override
		protected IllagerSpell getSpell() {
			return IllagerSpell.SUMMON_VEX;
		}
	}

	@Override
	public boolean canJoinRaid() {
		return false;
	}

	@Override
	public float getVoicePitch() {
		return VOICE_PITCH;
	}
}
