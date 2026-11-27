package com.jaymoskingdoms.mixin.illagers;

import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Illager archers that stand and shoot (illagers/RangedBowAttackWithoutStrafingGoal): no strafing, faster up close. */
@Mixin(RangedBowAttackGoal.class)
public interface RangedBowAttackGoalAccessor {
	@Accessor("strafingTime")
	void jaymoskingdoms$setStrafingTime(int ticks);

	@Accessor("attackIntervalMin")
	void jaymoskingdoms$setAttackIntervalMin(int ticks);

	@Accessor("attackRadiusSqr")
	float jaymoskingdoms$attackRadiusSqr();
}
