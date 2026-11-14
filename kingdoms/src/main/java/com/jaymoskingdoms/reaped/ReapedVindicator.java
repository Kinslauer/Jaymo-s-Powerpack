package com.jaymoskingdoms.reaped;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.level.Level;

/** A reaped vindicator ({@link Reaped}): the axe it was buried with. */
public class ReapedVindicator extends Vindicator implements Reaped {
	public ReapedVindicator(EntityType<? extends ReapedVindicator> type, Level level) {
		super(type, level);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, AbstractIllager.class, 10, true, false, (target, level) -> Reaped.prey(target)));
	}

	@Override
	protected boolean considersEntityAsAlly(Entity other) {
		return other == this || Reaped.ally(this, other);
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
