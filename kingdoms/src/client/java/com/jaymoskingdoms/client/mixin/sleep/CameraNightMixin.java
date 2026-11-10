package com.jaymoskingdoms.client.mixin.sleep;

import com.jaymoskingdoms.client.NightCam;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While the night runs by, the camera leaves the pillow for {@link NightCam}'s free view, and you see yourself asleep. */
@Mixin(Camera.class)
abstract class CameraNightMixin {
	@Shadow
	private boolean detached;
	@Shadow
	private float xRot;
	@Shadow
	private float yRot;
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	public abstract Vec3 position();

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	protected abstract void setPosition(double x, double y, double z);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymoskingdoms$nightCam(float partialTicks, CallbackInfo ci) {
		double[] f = NightCam.frame(this.minecraft, this.position(), this.yRot, this.xRot, partialTicks);
		if (f != null) {
			this.setPosition(f[0], f[1], f[2]);
			this.setRotation((float) f[3], (float) f[4]);
			this.detached = true;
		}
	}
}
