package com.jaymospowers.client.mixin;

import com.jaymospowers.client.wounds.BlackoutHush;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The game's sound fades with the screen going dark as you go down ({@link BlackoutHush}): a sound starting is hushed, and
 * one already playing follows the hush each time its volume is worked out again. Same two places the sound browser's
 * volume works in.
 */
@Mixin(SoundEngine.class)
abstract class BlackoutHushSoundMixin {
	@WrapOperation(method = "play", at = @At(value = "INVOKE", ordinal = 0,
		target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F"))
	private float jaymospowers$blackoutStart(SoundInstance sound, Operation<Float> original) {
		return original.call(sound) * BlackoutHush.volume(sound);
	}

	@ModifyReturnValue(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"))
	private float jaymospowers$blackoutTick(float volume, SoundInstance sound) {
		return volume * BlackoutHush.volume(sound);
	}
}
