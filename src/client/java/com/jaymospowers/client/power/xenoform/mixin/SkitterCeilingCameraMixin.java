package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.client.power.xenoform.SkitterCeiling;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.18.112, Scan E) Skitter on a ceiling: the view rolls over with the body ({@link SkitterCeiling}), and in first
 * person the eyes drop to where its upside-down head hangs.
 */
@Mixin(Camera.class)
abstract class SkitterCeilingCameraMixin {
	@Shadow
	private Entity entity;
	@Shadow
	private boolean detached;
	@Shadow
	private Vec3 position;
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
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$skitterCeiling(float partialTicks, CallbackInfo ci) {
		float flip = SkitterCeiling.amount(this.entity, partialTicks);
		if (flip <= 0.001F) {
			return;
		}
		if (!this.detached) {
			// eyes at the head, which now hangs at the bottom of its box
			double drop = (this.entity.getBbHeight() - 2.0 * this.entity.getEyeHeight()) * flip;
			this.setPosition(this.position.add(0.0, drop, 0.0));
		}
		this.rotation.rotateZ((float)Math.PI * flip);
		this.up.set(0.0F, 1.0F, 0.0F).rotate(this.rotation);
		this.left.set(-1.0F, 0.0F, 0.0F).rotate(this.rotation);
		this.matrixPropertiesDirty |= 3;
	}
}
