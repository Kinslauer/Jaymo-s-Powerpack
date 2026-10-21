package com.jaymoskingdoms.client.mixin.shouldersurfing;

import com.jaymoskingdoms.client.equestrian.FreeReinClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.7.113) Shoulder Surfing Reloaded (optional, nothing of it copied) turns you to its camera every tick while the
 * camera is coupled, which pinned free rein riders: the keys couldn't turn the horse. Under free rein it leaves your
 * heading to {@link FreeReinClient}. Skipped when Shoulder Surfing isn't there.
 */
@Pseudo
@Mixin(targets = "com.github.exopandora.shouldersurfing.client.ShoulderSurfing", remap = false)
abstract class ShoulderSurfingMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;setXRot(F)V"), require = 0)
	private void jaymoskingdoms$freeReinPitch(LocalPlayer player, float pitch, Operation<Void> original) {
		if (!FreeReinClient.on()) {
			original.call(player, pitch);
		}
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;setYRot(F)V"), require = 0)
	private void jaymoskingdoms$freeReinYaw(LocalPlayer player, float yaw, Operation<Void> original) {
		if (!FreeReinClient.on()) {
			original.call(player, yaw);
		}
	}
}
