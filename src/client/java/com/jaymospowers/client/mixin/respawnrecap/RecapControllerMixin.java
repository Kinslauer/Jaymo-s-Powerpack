package com.jaymospowers.client.mixin.respawnrecap;

import com.jaymospowers.client.power.chrono.ChronoCheatDeathClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.20.50) Respawn Recap (MIT, by DialingSpoon; github.com/DialingSpoon527/Respawn-Recap; shipped unchanged, nothing of it
 * copied) plays its memory replay whenever you're dead. Andy: "limit it to chrono". So, when it's installed:
 * <ul>
 * <li>only Chrono's rewind out of death ({@link ChronoCheatDeathClient}) counts as dead to it, so its replay plays as the
 * rewind and never for a real death;</li>
 * <li>at the replay's end it asks no respawn (you're alive), it only ends the film.</li>
 * </ul>
 * Its death-screen takeover is switched off in {@code client/compat/RespawnRecapPlugin}.
 */
@Pseudo
@Mixin(targets = "net.dialingspoon.respawnrecap.client.RecapController", remap = false)
abstract class RecapControllerMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isDeadOrDying()Z"), require = 0)
	private static boolean jaymospowers$onlyRewindIsDeath(LocalPlayer player) {
		return ChronoCheatDeathClient.recapping();
	}

	@Redirect(method = "finishReplay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;respawn()V"), require = 0)
	private static void jaymospowers$rewindNeedsNoRespawn(LocalPlayer player) {
		ChronoCheatDeathClient.recapDone();
	}

	@Inject(method = "finishRespawn", at = @At("TAIL"), require = 0)
	private static void jaymospowers$recapOver(CallbackInfo ci) {
		ChronoCheatDeathClient.recapIdle();
	}
}
