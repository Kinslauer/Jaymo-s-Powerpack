package com.jaymospowers.client.mixin.respawnrecap;

import com.jaymospowers.client.UltimateCinematic;
import com.jaymospowers.client.power.chrono.ChronoCheatDeathClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * (0.20.42) Respawn Recap (MIT, by DialingSpoon; github.com/DialingSpoon527/Respawn-Recap; shipped unchanged, nothing of it
 * copied) plays its memory replay whenever you're dead. Two nudges, skipped when it isn't installed:
 * <ul>
 * <li>Chrono's rewind out of death ({@link ChronoCheatDeathClient}) counts as dead, so the replay plays as the rewind;
 * at its end it asks no respawn (you're alive), it only ends the film.</li>
 * <li>While a film plays over your body ({@link UltimateCinematic#deathFilm}), it waits for it.</li>
 * </ul>
 */
@Pseudo
@Mixin(targets = "net.dialingspoon.respawnrecap.client.RecapController", remap = false)
abstract class RecapControllerMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isDeadOrDying()Z"), require = 0)
	private static boolean jaymospowers$rewindIsDeath(LocalPlayer player) {
		if (ChronoCheatDeathClient.recapping()) {
			return true;
		}
		return player.isDeadOrDying() && !UltimateCinematic.deathFilm();
	}

	@Redirect(method = "finishReplay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;respawn()V"), require = 0)
	private static void jaymospowers$rewindNeedsNoRespawn(LocalPlayer player) {
		if (ChronoCheatDeathClient.recapping()) {
			ChronoCheatDeathClient.recapDone();
			return;
		}
		player.respawn();
	}
}
