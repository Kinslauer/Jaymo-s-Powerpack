package com.jaymospowers.client.mixin;

import com.jaymospowers.client.SneakZoom;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Zoomed in with Shift + Left Alt ({@link SneakZoom}), the mouse turns the view slower by as much as the view is zoomed. */
@Mixin(MouseHandler.class)
abstract class ZoomMouseMixin {
	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
	private double jaymospowers$zoomYaw(double yaw) {
		return yaw * SneakZoom.turnFactor();
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
	private double jaymospowers$zoomPitch(double pitch) {
		return pitch * SneakZoom.turnFactor();
	}
}
