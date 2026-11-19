package com.jaymospowers.client.mixin;

import com.jaymospowers.client.MountClimbPose;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Climbing onto or off a mount, in first person: the view rides up into the saddle with the body, and down again (MountClimbPose). */
@Mixin(Camera.class)
abstract class MountClimbCameraMixin {
	@Shadow
	private boolean detached;
	@Shadow
	private Entity entity;

	@Shadow
	public abstract Vec3 position();

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$climbView(float partialTicks, CallbackInfo ci) {
		if (this.detached || !(this.entity instanceof Player player) || player.isPassenger()) {
			return;
		}
		Vec3 shift = MountClimbPose.eyeShift(player, partialTicks);
		if (shift != null) {
			this.setPosition(this.position().add(shift));
		}
	}
}
