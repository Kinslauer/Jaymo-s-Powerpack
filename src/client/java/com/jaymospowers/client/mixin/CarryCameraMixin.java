package com.jaymospowers.client.mixin;

import com.jaymospowers.client.CarryPose;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Carried, in first person: the view goes where your head is in the hold (over a shoulder, across the arms...). */
@Mixin(Camera.class)
abstract class CarryCameraMixin {
	@Shadow
	private boolean detached;
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$carriedView(float partialTicks, CallbackInfo ci) {
		if (this.detached) {
			return;
		}
		Vec3 eyes = CarryPose.carriedEyes(this.minecraft, partialTicks);
		if (eyes != null) {
			this.setPosition(eyes);
		}
	}
}
