package com.jaymospowers.client.power.wind.mixin;

import com.jaymospowers.client.power.wind.WindCamera;
import com.jaymospowers.client.power.wind.WindHoverSway;
import com.jaymospowers.client.power.wind.WindPowerClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wind camera tremble: nudges the first-person view (not the player's aim) at sonic speed, and lets it ride
 * the hovering body's sway.
 */
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
	private Vec3 position;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$windShake(float partialTicks, CallbackInfo ci) {
		if (this.detached || !WindPowerClient.cameraEffectsActive(this.minecraft)) {
			return;
		}
		float[] offsets = WindCamera.offsets(partialTicks);
		if (offsets != null) {
			this.setRotation(this.yRot + offsets[0], Math.clamp(this.xRot + offsets[1], -90.0F, 90.0F));
		}
		// hovering: the view rides the swaying body, a fraction of a degree and a little bob
		float[] hover = WindHoverSway.viewOffsets(this.minecraft, partialTicks);
		if (hover != null) {
			this.setRotation(this.yRot + hover[0], Math.clamp(this.xRot + hover[1], -90.0F, 90.0F));
			this.setPosition(this.position.add(0.0, hover[2], 0.0));
		}
	}
}
