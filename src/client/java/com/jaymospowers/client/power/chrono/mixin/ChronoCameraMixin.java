package com.jaymospowers.client.power.chrono.mixin;

import com.jaymospowers.client.power.chrono.ChronoFlowClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Time Flow's view: third person, closer in and over the right shoulder, so you see your blows and what's ahead. */
@Mixin(Camera.class)
abstract class ChronoCameraMixin {
	/** How far behind the eyes, how far to the right and how far up the camera sits (blocks). */
	private static final double SHOULDER_BACK = 2.6, SHOULDER_SIDE = 0.75, SHOULDER_UP = 0.15;
	/** Kept this far off a wall the camera would otherwise sink into. */
	private static final double WALL_MARGIN = 0.2;

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
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$overShoulder(float partialTicks, CallbackInfo ci) {
		if (!this.detached || !ChronoFlowClient.overShoulder(this.minecraft)) {
			return;
		}
		Entity entity = this.minecraft.getCameraEntity();
		Vec3 eyes = entity.getEyePosition(partialTicks);
		Vec3 look = Vec3.directionFromRotation(this.xRot, this.yRot);
		double yaw = Math.toRadians(this.yRot);
		Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
		Vec3 wanted = eyes.subtract(look.scale(SHOULDER_BACK)).add(right.scale(SHOULDER_SIDE)).add(0.0, SHOULDER_UP, 0.0);
		HitResult hit = entity.level().clip(new ClipContext(eyes, wanted, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
		if (hit.getType() != HitResult.Type.MISS) {
			Vec3 back = hit.getLocation().subtract(eyes);
			double length = back.length();
			wanted = length > WALL_MARGIN ? eyes.add(back.scale((length - WALL_MARGIN) / length)) : eyes;
		}
		this.setPosition(wanted);
	}
}
