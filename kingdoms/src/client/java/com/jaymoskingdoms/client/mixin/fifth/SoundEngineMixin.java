package com.jaymoskingdoms.client.mixin.fifth;

import com.jaymoskingdoms.client.FifthClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Deafened: every sound but the whine goes muffled ({@link FifthClient#muffle}). */
@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
	@Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$muffle(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
		float share = FifthClient.muffle(sound);
		if (share < 1.0F) {
			cir.setReturnValue(cir.getReturnValueF() * share);
		}
	}
}
