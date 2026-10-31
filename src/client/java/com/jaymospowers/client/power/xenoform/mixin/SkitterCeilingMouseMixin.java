package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.client.power.xenoform.SkitterCeiling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** (0.18.112, Scan E) Skitter on a ceiling, its view rolled over: the mouse turns the way the screen shows. */
@Mixin(MouseHandler.class)
abstract class SkitterCeilingMouseMixin {
	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0)
	private double jaymospowers$skitterYaw(double yaw) {
		return SkitterCeiling.overturned(Minecraft.getInstance().player) ? -yaw : yaw;
	}

	@ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1)
	private double jaymospowers$skitterPitch(double pitch) {
		return SkitterCeiling.overturned(Minecraft.getInstance().player) ? -pitch : pitch;
	}
}
