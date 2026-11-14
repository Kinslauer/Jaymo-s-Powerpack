package com.jaymoskingdoms.reaped;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.level.Level;

/** A reaped pillager ({@link Reaped}): the crossbow still in its hands. */
public class ReapedPillager extends Pillager implements Reaped {
	public ReapedPillager(EntityType<? extends ReapedPillager> type, Level level) {
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

	@Override
	public boolean canJoinRaid() {
		return false;
	}

	@Override
	public float getVoicePitch() {
		return VOICE_PITCH;
	}
}
