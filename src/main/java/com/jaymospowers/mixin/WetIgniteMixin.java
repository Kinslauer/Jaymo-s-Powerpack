package com.jaymospowers.mixin;

import com.jaymospowers.core.wet.Wet;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A wet body catches fire for less long ({@link Wet#ignite}). */
@Mixin(Entity.class)
abstract class WetIgniteMixin {
	@ModifyVariable(method = "igniteForTicks", at = @At("HEAD"), argsOnly = true)
	private int jaymospowers$wetIgnite(int ticks) {
		return Wet.ignite((Entity) (Object) this, ticks);
	}
}
