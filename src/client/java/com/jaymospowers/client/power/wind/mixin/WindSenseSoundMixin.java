package com.jaymospowers.client.power.wind.mixin;

import com.jaymospowers.client.power.wind.WindAirSense;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (Jung) The airflow sense's focus quiets the game ({@link WindAirSense#hush}): a sound that starts while it is on starts
 * hushed, and one already playing follows it down and back (each time {@code WindAirSense} sets the categories again,
 * and as a sound that keeps its volume up to date goes on). Music too: only menu clicks, the master category and the
 * focus's own breeze are left alone. Same two places the sound browser's volume works in.
 */
@Mixin(SoundEngine.class)
abstract class WindSenseSoundMixin {
	@WrapOperation(method = "play", at = @At(value = "INVOKE", ordinal = 0,
		target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F"))
	private float jaymospowers$senseHushStart(SoundInstance sound, Operation<Float> original) {
		return original.call(sound) * jaymospowers$hush(sound);
	}

	@ModifyReturnValue(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"))
	private float jaymospowers$senseHushTick(float volume, SoundInstance sound) {
		return volume * jaymospowers$hush(sound);
	}

	private static float jaymospowers$hush(SoundInstance sound) {
		SoundSource source = sound.getSource();
		if (source == SoundSource.UI || source == SoundSource.MASTER || WindAirSense.isBreeze(sound)) {
			return 1.0F;
		}
		return WindAirSense.hush();
	}
}
