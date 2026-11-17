package com.jaymospowers.mixin;

import com.jaymospowers.core.TeamTime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (Awakening pick) A power's frost on an enemy player freezes the caster's teammates near them too ({@link TeamTime#frozen}). */
@Mixin(Entity.class)
abstract class TeamTimeFrozenMixin {
	@Inject(method = "setTicksFrozen", at = @At("HEAD"))
	private void jaymospowers$teamTime(int ticks, CallbackInfo ci) {
		if ((Object)this instanceof LivingEntity living) {
			TeamTime.frozen(living, ticks);
		}
	}
}
