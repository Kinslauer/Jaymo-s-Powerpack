package com.jaymospowers.client.mixin;

import com.jaymospowers.client.FrontCamera;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * F5 goes between first person and the view from behind only: the view from the front has its own key
 * ({@link FrontCamera}). With Shoulder Surfing in, its own F5 runs instead ({@code ShoulderSurfingFrontMixin}); this is
 * vanilla's, for when it isn't.
 */
@Mixin(Minecraft.class)
abstract class FrontCameraCycleMixin {
	@ModifyExpressionValue(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;cycle()Lnet/minecraft/client/CameraType;"))
	private CameraType jaymospowers$skipFront(CameraType next) {
		return next == CameraType.THIRD_PERSON_FRONT ? next.cycle() : next;
	}
}
