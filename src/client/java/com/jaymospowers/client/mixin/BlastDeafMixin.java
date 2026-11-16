package com.jaymospowers.client.mixin;

import com.jaymospowers.client.blast.BlastFx;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ears ringing from a blast beside you (BlastFx): sounds that start while it lasts play quieter (not the ringing, menus or music). */
@Mixin(SoundEngine.class)
abstract class BlastDeafMixin {
	@Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$deaf(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
		float hearing = BlastFx.hearing(sound);
		if (hearing < 1.0F) {
			cir.setReturnValue(cir.getReturnValueF() * hearing);
		}
	}
}
