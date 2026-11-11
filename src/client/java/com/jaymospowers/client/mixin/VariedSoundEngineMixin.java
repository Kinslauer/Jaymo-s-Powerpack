package com.jaymospowers.client.mixin;

import com.jaymospowers.client.sound.Varied;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** One-shot sounds never come back at quite the same pitch twice running (see Varied). */
@Mixin(SoundEngine.class)
abstract class VariedSoundEngineMixin {
	@ModifyReturnValue(method = "calculatePitch", at = @At("RETURN"))
	private float jaymospowers$varied(float pitch, SoundInstance sound) {
		return Varied.pitch(sound, pitch);
	}
}
