package com.jaymospowers.client.mixin;

import com.jaymospowers.client.sound.SoundTuning;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The sound browser's settings: muted and thinned sounds never start, and each sound plays at its own volume (see SoundTuning). */
@Mixin(SoundEngine.class)
abstract class SoundTuningEngineMixin {
	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$tuned(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (!SoundTuning.starts(sound)) {
			cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}

	@WrapOperation(method = "play", at = @At(value = "INVOKE", ordinal = 0,
		target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F"))
	private float jaymospowers$tunedVolume(SoundInstance sound, Operation<Float> original) {
		return original.call(sound) * SoundTuning.level(sound.getIdentifier());
	}

	@ModifyReturnValue(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"))
	private float jaymospowers$tunedTick(float volume, SoundInstance sound) {
		return volume * SoundTuning.level(sound.getIdentifier());
	}
}
