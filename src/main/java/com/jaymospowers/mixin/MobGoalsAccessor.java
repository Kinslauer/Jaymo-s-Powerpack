package com.jaymospowers.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A mob's goals, from outside it (the future's gunners give up their melee rush: {@code FutureGuns}). */
@Mixin(Mob.class)
public interface MobGoalsAccessor {
	@Accessor("goalSelector")
	GoalSelector jaymospowers$goals();
}
