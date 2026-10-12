package com.jaymospowers.client.mixin;

import com.jaymospowers.client.ShoulderCamera;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The shoulder view (see ShoulderCamera): instead of vanilla's spot 4 blocks straight behind, the camera sits a
 * little to the right of the eyes and closer behind, pulled in wherever a wall is in the way.
 */
@Mixin(Camera.class)
abstract class ShoulderCameraMixin {
	@Shadow
	private boolean detached;
	@Shadow
	private Entity entity;
	@Shadow
	private Level level;
	@Shadow
	private float eyeHeight;
	@Shadow
	private float eyeHeightOld;

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void move(float forwards, float up, float right);

	@Shadow
	private float getMaxZoom(float cameraDist) {
		throw new AssertionError();
	}

	@Shadow
	public abstract Vec3 position();


	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$overTheShoulder(float partialTicks, CallbackInfo ci) {
		if (!this.detached || this.entity.isPassenger() || !ShoulderCamera.inView()) {
			return;
		}
		Vec3 eyes = new Vec3(Mth.lerp(partialTicks, this.entity.xo, this.entity.getX()),
			Mth.lerp(partialTicks, this.entity.yo, this.entity.getY()) + Mth.lerp(partialTicks, this.eyeHeightOld, this.eyeHeight),
			Mth.lerp(partialTicks, this.entity.zo, this.entity.getZ()));
		float scale = this.entity instanceof LivingEntity living ? living.getScale() : 1.0F;
		// out to the right shoulder first, as far as nothing's in the way
		this.setPosition(eyes);
		this.move(0.0F, ShoulderCamera.UP * scale, ShoulderCamera.SIDE * scale);
		Vec3 side = this.position().subtract(eyes);
		HitResult wall = this.level.clip(new ClipContext(eyes, eyes.add(side.scale(1.3)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.entity));
		double room = wall.getType() == HitResult.Type.MISS ? 1.0 : Math.max(0.0, wall.getLocation().distanceTo(eyes) / (side.length() * 1.3) - 0.15);
		this.setPosition(eyes.add(side.scale(Math.min(1.0, room))));
		// then back, as vanilla does, but closer
		this.move(-this.getMaxZoom(ShoulderCamera.DISTANCE * scale), 0.0F, 0.0F);
	}
}
