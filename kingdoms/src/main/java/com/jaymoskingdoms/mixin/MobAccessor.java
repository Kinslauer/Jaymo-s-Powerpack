package com.jaymoskingdoms.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.5.21) A mob's targets, so {@code Prey} can set monsters on a settlement's folk; (0.7.97) and its goals, so
 * {@code wild.SpiderMinds} can give every spider its own. */
@Mixin(Mob.class)
public interface MobAccessor {
	@Accessor("targetSelector")
	GoalSelector jaymoskingdoms$targets();

	@Accessor("goalSelector")
	GoalSelector jaymoskingdoms$goals();
}
