package com.jaymospowers.power.chrono.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Overclock's weapon flurry: each landing blow is a fully charged one ({@code ChronoFlurry}). */
@Mixin(LivingEntity.class)
public interface ChronoAttackStrengthAccessor {
	@Accessor("attackStrengthTicker")
	void jaymospowers$setAttackStrengthTicker(int ticks);
}
