package com.jaymospowers.client.power.wind.mixin;

import com.jaymospowers.client.power.wind.WindCamera;
import com.jaymospowers.client.power.wind.WindPowerClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wind camera shake: nudges the first-person view (not the player's aim) by a few decaying degrees. */
@Mixin(Camera.class)
abstract class CameraMixin {
	@Shadow
	private float xRot;
	@Shadow
	private float yRot;
	@Shadow
	private boolean detached;
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$windShake(float partialTicks, CallbackInfo ci) {
		if (this.detached || !WindPowerClient.cameraEffectsActive(this.minecraft)) {
			return;
		}
		float[] offsets = WindCamera.offsets(partialTicks);
		if (offsets != null) {
			this.setRotation(this.yRot + offsets[0], Math.clamp(this.xRot + offsets[1], -90.0F, 90.0F));
		}
	}
}
