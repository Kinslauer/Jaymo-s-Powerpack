package com.jaymospowers.client.mixin;

import com.jaymospowers.client.UltimateCinematic;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.16.24) An ultimate's film ({@link UltimateCinematic}) moves and turns the camera. A late priority, so it comes after
 * every other view (Shoulder Surfing's, the giant's, Time Flow's; the beam battle's film never runs with it) and has the last word.
 */
@Mixin(value = Camera.class, priority = 2000)
abstract class UltimateCinematicCameraMixin {
	@Shadow
	private boolean detached;
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	private Quaternionf rotation;
	@Shadow
	@Final
	private Vector3f up;
	@Shadow
	@Final
	private Vector3f left;
	@Shadow
	private int matrixPropertiesDirty;

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$ultimateFilm(float partialTicks, CallbackInfo ci) {
		double[] view = UltimateCinematic.view(this.minecraft, partialTicks);
		if (view != null) {
			this.setRotation((float)view[3], (float)view[4]);
			if (view.length > 5 && view[5] != 0.0) {
				// (0.18.46) roll: vanilla's rotation is yaw and pitch only (its third angle is 0), so turn the camera about its own
				// forward axis and refresh the up and left vectors (forwards is unchanged); the view matrices are dirtied again
				this.rotation.rotateZ((float)Math.toRadians(view[5]));
				new Vector3f(0.0F, 1.0F, 0.0F).rotate(this.rotation, this.up);
				new Vector3f(-1.0F, 0.0F, 0.0F).rotate(this.rotation, this.left);
				this.matrixPropertiesDirty |= 3;
			}
			this.setPosition(view[0], view[1], view[2]);
			this.detached = true;
		}
	}
}
