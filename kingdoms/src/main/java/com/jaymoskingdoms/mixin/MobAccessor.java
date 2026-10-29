package com.jaymoskingdoms.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.5.21) A mob's targets, so {@code Prey} can set monsters on a settlement's folk; (0.7.97) and its goals, so
 * {@code wild.SpiderMinds} can give every spider its own; (0.7.338) and its own move control, the field rather than
 * {@code getMoveControl()} (which is the vehicle's while riding), for {@code mca.McaMountedArcherMixin}. */
@Mixin(Mob.class)
public interface MobAccessor {
	@Accessor("targetSelector")
	GoalSelector jaymoskingdoms$targets();

	@Accessor("goalSelector")
	GoalSelector jaymoskingdoms$goals();

	@Accessor("moveControl")
	MoveControl<?> jaymoskingdoms$moveControl();
}
