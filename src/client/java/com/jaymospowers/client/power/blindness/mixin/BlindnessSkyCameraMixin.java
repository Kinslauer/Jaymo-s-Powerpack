package com.jaymospowers.client.power.blindness.mixin;

import com.jaymospowers.client.power.blindness.BlindnessDevoutSight;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sky Sight ({@link BlindnessDevoutSight}): the devout's view rises high over them and looks down, in any view. */
@Mixin(Camera.class)
abstract class BlindnessSkyCameraMixin {
	@Shadow
	private boolean detached;
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$skySight(float partialTicks, CallbackInfo ci) {
		if (this.minecraft.player == null || this.minecraft.getCameraEntity() != this.minecraft.player) {
			return;
		}
		double[] view = BlindnessDevoutSight.view(this.minecraft, this.minecraft.player.getEyePosition(partialTicks), partialTicks);
		if (view != null) {
			this.setRotation((float)view[3], (float)view[4]);
			this.setPosition(view[0], view[1], view[2]);
			// drawn from outside: their own body shows
			this.detached = true;
		}
	}
}
