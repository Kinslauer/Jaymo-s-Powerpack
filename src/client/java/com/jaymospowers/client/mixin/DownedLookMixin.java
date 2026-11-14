package com.jaymospowers.client.mixin;

import com.jaymospowers.client.wounds.OutOfBodyClient;
import com.jaymospowers.core.wounds.Downed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Andy (2026-11-14): "Im also able to move side to side with the camera while passed out, that doesnt make sense". Out
 * cold (passed out, or knocked out) the mouse no longer turns the view, nor the body lying with it: it stays as it fell.
 * Out of the body as a ghost ({@code OutOfBodyClient}) the ghost still looks about.
 */
@Mixin(MouseHandler.class)
abstract class DownedLookMixin {
	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
	private double jaymospowers$outColdYaw(double yaw) {
		return outCold() ? 0.0 : yaw;
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
	private double jaymospowers$outColdPitch(double pitch) {
		return outCold() ? 0.0 : pitch;
	}

	private static boolean outCold() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && Downed.isDowned(player) && !Downed.awake(player) && !OutOfBodyClient.drifting();
	}
}
