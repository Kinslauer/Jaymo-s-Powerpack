package com.jaymoskingdoms.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.5.13) A mob's targets, so {@code Prey} can set monsters on a settlement's folk. */
@Mixin(Mob.class)
public interface MobAccessor {
	@Accessor("targetSelector")
	GoalSelector jaymoskingdoms$targets();
}
