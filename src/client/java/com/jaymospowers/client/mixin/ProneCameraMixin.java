package com.jaymospowers.client.mixin;

import com.jaymospowers.client.CrawlPose;
import com.jaymospowers.client.ProneTransition;
import com.jaymospowers.client.wounds.WallSit;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.19.9) Prone, in first person: the view goes down (and up) with the head as you crouch into the crawl or get up from
 * it ({@link ProneTransition}), and follows the head round when you turn over onto your back ({@link CrawlPose}).
 * Downed and sat against a wall (or getting up against one), it's at the model's own eyes ({@link WallSit#eye}).
 */
@Mixin(Camera.class)
abstract class ProneCameraMixin {
	@Shadow
	private boolean detached;
	@Shadow
	private Entity entity;

	@Shadow
	public abstract Vec3 position();

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$proneView(float partialTicks, CallbackInfo ci) {
		if (this.detached || !(this.entity instanceof Player player) || player.isPassenger()) {
			return;
		}
		Vec3 at = this.position();
		Vec3 sat = WallSit.eye(player, partialTicks, at);
		if (sat != null) {
			// (Andy, 2026-11-15: "the view needs to move to the exact height on the model") downed, sat against a wall
			this.setPosition(sat);
			return;
		}
		Float eye = ProneTransition.eyeHeight(player, partialTicks);
		if (eye != null) {
			at = new Vec3(at.x, Mth.lerp(partialTicks, player.yo, player.getY()) + eye, at.z);
		}
		Vec3 shift = CrawlPose.supineEyeShift(player, partialTicks);
		if (shift != null) {
			at = at.add(shift);
		}
		if (eye != null || shift != null) {
			this.setPosition(at);
		}
	}
}
