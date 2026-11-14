package com.jaymoskingdoms.reaped;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.level.Level;

/** A reaped evoker ({@link Reaped}): it still casts, and its vexes still come. */
public class ReapedEvoker extends Evoker implements Reaped {
	public ReapedEvoker(EntityType<? extends ReapedEvoker> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
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

	@Override
	public boolean canJoinRaid() {
		return false;
	}

	@Override
	public float getVoicePitch() {
		return VOICE_PITCH;
	}
}
