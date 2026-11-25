package com.jaymospowers.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Ambient Creatures' penguin tilts its pitch to its swimming speed with {@code Mth.atan2(y, sqrt(x*y + z))} (a typo for
 * {@code x*x + z*z}), which is NaN whenever {@code x*y + z} is negative. Minecraft throws the NaN away with an "Invalid entity rotation" error line, hundreds a
 * minute near penguins. A non-finite pitch is swapped for the one it already has.
 */
@Pseudo
@Mixin(targets = "com.victor.ambient_creatures.world.entity.animal.Penguin", remap = false)
abstract class PenguinSwimPitchMixin {
	@ModifyArg(method = "travel", at = @At(value = "INVOKE",
		target = "Lcom/victor/ambient_creatures/world/entity/animal/Penguin;setXRot(F)V"), require = 0)
	private float jaymospowers$finitePitch(float pitch) {
		return Float.isFinite(pitch) ? pitch : ((Entity) (Object) this).getXRot();
	}
}
