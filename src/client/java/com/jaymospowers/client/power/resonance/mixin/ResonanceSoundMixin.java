package com.jaymospowers.client.power.resonance.mixin;

import com.jaymospowers.client.power.resonance.ResonanceHushClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hush: a sound inside a dome of silence (or heard from inside one) never starts. */
@Mixin(SoundEngine.class)
abstract class ResonanceSoundMixin {
	@Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
		at = @At("HEAD"), cancellable = true)
	private void jaymospowers$resonanceHush(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (ResonanceHushClient.muted(sound)) {
			cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}
}
