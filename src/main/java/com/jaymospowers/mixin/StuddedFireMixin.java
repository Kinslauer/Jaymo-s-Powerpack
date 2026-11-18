package com.jaymospowers.mixin;

import com.jaymospowers.tinkering.smithing.StuddedWeather;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A full studded set shortens every burning set alight ({@link StuddedWeather#fireCut}). */
@Mixin(Entity.class)
abstract class StuddedFireMixin {
	@ModifyVariable(method = "igniteForTicks", at = @At("HEAD"), argsOnly = true)
	private int jaymospowers$shorter(int ticks) {
		if ((Object)this instanceof LivingEntity living && !living.level().isClientSide()) {
			return Math.round(ticks * (1.0F - StuddedWeather.fireCut(living)));
		}
		return ticks;
	}
}
