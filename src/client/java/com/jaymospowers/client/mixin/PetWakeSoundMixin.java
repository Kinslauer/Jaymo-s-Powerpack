package com.jaymospowers.client.mixin;

import com.jaymospowers.client.pets.PetPoses;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (Motion patch 6) A loud sound in the world wakes dozing animals near it (PetPoses). */
@Mixin(SoundManager.class)
abstract class PetWakeSoundMixin {
	@Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;", at = @At("RETURN"))
	private void jaymospowers$wake(SoundInstance sound, CallbackInfoReturnable<?> cir) {
		if (sound.isRelative() || sound.getSource() == SoundSource.MUSIC || sound.getSource() == SoundSource.AMBIENT || sound.getSource() == SoundSource.UI) {
			return;
		}
		// played, the sound is resolved, so its volume can be read (a sound that failed to resolve is skipped)
		float volume;
		try {
			volume = sound.getVolume();
		} catch (RuntimeException e) {
			return;
		}
		PetPoses.heard(sound.getX(), sound.getY(), sound.getZ(), volume);
	}
}
