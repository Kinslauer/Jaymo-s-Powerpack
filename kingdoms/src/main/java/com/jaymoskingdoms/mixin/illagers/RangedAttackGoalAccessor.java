package com.jaymoskingdoms.mixin.illagers;

import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A marauder (illagers/Marauder) shows its throw's wind-up from the goal's clock. */
@Mixin(RangedAttackGoal.class)
public interface RangedAttackGoalAccessor {
	@Accessor("attackTime")
	int jaymoskingdoms$attackTime();
}
