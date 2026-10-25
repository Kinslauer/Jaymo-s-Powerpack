package com.jaymospowers.client.mixin.shouldersurfing;

import com.jaymospowers.client.GiantCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.16.6) Andy: "the camera isn't working on third or first person ape mode. you still cant see your character at all".
 * Shoulder Surfing Reloaded (MIT; optional, nothing of it copied) skips drawing you when its own camera, a few blocks
 * back, sits within your width: a 10-block-wide Great Ape always does, so the ape vanished from his own view though
 * {@link GiantCamera} had already put the view 25 blocks back. While the giant's view is in use, you're drawn whole.
 * Skipped when Shoulder Surfing isn't there.
 */
@Pseudo
@Mixin(targets = "com.github.exopandora.shouldersurfing.client.renderer.CameraEntityRenderer", remap = false)
abstract class ShoulderSurfingGiantMixin {
	@Inject(method = "preRenderCameraEntity", at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymospowers$drawGiant(Entity entity, float partialTicks, CallbackInfoReturnable<Boolean> cir) {
		Minecraft minecraft = Minecraft.getInstance();
		if (entity == minecraft.player && (GiantCamera.active(minecraft) || com.jaymospowers.client.power.zfighter.ZFighterSpiritCamera.pulling())) {
			// false: draw him; and without SS's flag set, none of its fading is laid on him
			cir.setReturnValue(false);
		}
	}
}
