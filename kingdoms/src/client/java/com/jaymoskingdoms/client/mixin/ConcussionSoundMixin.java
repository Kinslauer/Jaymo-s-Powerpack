package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.client.inhabitants.Concussed;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A concussed player hears the world muffled: sounds that start while it lasts play quieter (not the ringing). */
@Mixin(SoundEngine.class)
abstract class ConcussionSoundMixin {
	@Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$muffled(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
		if (Concussed.now() && !Concussed.heardClearly(sound)) {
			cir.setReturnValue(cir.getReturnValueF() * Concussed.MUFFLED);
		}
	}
}
