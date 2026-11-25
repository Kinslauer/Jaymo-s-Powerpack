package com.jaymospowers.client.rig.mixin;

import com.jaymospowers.client.rig.RigKickCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The combo roundhouse swings your camera all the way round you (see RigKickCamera); before an ultimate's film, which never runs with it. */
@Mixin(value = Camera.class, priority = 1900)
abstract class RigKickCameraMixin {
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
	private void jaymospowers$roundhouseSpin(float partialTicks, CallbackInfo ci) {
		double[] view = RigKickCamera.view(this.minecraft, partialTicks);
		if (view != null) {
			this.setRotation((float)view[3], (float)view[4]);
			this.setPosition(view[0], view[1], view[2]);
			this.detached = true;
		}
	}
}
