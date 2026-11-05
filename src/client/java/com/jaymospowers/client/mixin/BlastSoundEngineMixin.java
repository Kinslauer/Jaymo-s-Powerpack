package com.jaymospowers.client.mixin;

import com.jaymospowers.client.sound.Blasts;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Channel;
import java.util.function.Consumer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.20.56) Blasts take time to reach you and are dulled by distance (see Blasts). */
@Mixin(SoundEngine.class)
abstract class BlastSoundEngineMixin {
	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$blastDelay(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (Blasts.onPlay(sound, ((SoundEngine)(Object)this).getListenerTransform().position())) {
			cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}

	@ModifyArg(method = "play", at = @At(value = "INVOKE", ordinal = 0,
		target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"))
	private Consumer<Channel> jaymospowers$blastMuffle(Consumer<Channel> setup, @Local(argsOnly = true) SoundInstance sound) {
		if (!Blasts.muffled(sound)) {
			return setup;
		}
		return setup.andThen(channel -> Blasts.absorb(((ChannelSourceAccessor)channel).jaymospowers$source()));
	}
}
