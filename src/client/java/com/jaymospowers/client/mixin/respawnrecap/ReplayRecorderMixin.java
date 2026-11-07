package com.jaymospowers.client.mixin.respawnrecap;

import com.jaymospowers.client.power.chrono.ChronoCheatDeathClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.20.50) Respawn Recap keeps its screenshots of your life only while you're a Chrono (no one else's are ever replayed). */
@Pseudo
@Mixin(targets = "net.dialingspoon.respawnrecap.client.ReplayRecorder", remap = false)
abstract class ReplayRecorderMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymospowers$onlyChrono(Minecraft minecraft, CallbackInfo ci) {
		if (!ChronoCheatDeathClient.recordsLife(minecraft)) {
			ci.cancel();
		}
	}
}
