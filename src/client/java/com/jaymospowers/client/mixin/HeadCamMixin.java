package com.jaymospowers.client.mixin;

import com.jaymospowers.client.Dismembered;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Beheaded, in first person: the view is locked inside the fallen head and tumbles with it (see Dismembered). */
@Mixin(Camera.class)
abstract class HeadCamMixin {
	@Shadow
	private boolean detached;
	@Shadow
	private Entity entity;
	@Shadow
	private float xRot;
	@Shadow
	private float yRot;
	@Shadow
	@Final
	private Quaternionf rotation;
	@Shadow
	@Final
	private Vector3f forwards;
	@Shadow
	@Final
	private Vector3f up;
	@Shadow
	@Final
	private Vector3f left;
	@Shadow
	private int matrixPropertiesDirty;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$headCam(float partialTicks, CallbackInfo ci) {
		if (this.detached) {
			return;
		}
		Vector3d eye = new Vector3d();
		Quaternionf view = Dismembered.headView(this.entity, partialTicks, eye);
		if (view == null) {
			return;
		}
		this.rotation.set(view);
		this.forwards.set(0.0F, 0.0F, -1.0F).rotate(view);
		this.up.set(0.0F, 1.0F, 0.0F).rotate(view);
		this.left.set(-1.0F, 0.0F, 0.0F).rotate(view);
		// the plain angles of where the face points, for whatever reads those instead of the rotation
		this.xRot = (float) Math.toDegrees(Math.asin(Math.clamp(-this.forwards.y, -1.0F, 1.0F)));
		this.yRot = (float) Math.toDegrees(Math.atan2(-this.forwards.x, this.forwards.z));
		this.matrixPropertiesDirty |= 3;
		this.setPosition(new Vec3(eye.x, eye.y, eye.z));
	}
}
