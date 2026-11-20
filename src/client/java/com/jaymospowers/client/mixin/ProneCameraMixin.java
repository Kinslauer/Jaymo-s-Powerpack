package com.jaymospowers.client.mixin;

import com.jaymospowers.client.CrawlPose;
import com.jaymospowers.client.ProneTransition;
import com.jaymospowers.client.wounds.WallSit;
import com.jaymospowers.core.wounds.Downed;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.19.9) Prone, in first person: the view goes down (and up) with the head as you crouch into the crawl or get up from
 * it ({@link ProneTransition}), and follows the head round when you turn over onto your back ({@link CrawlPose}).
 * Downed and sat against a wall (or getting up against one), it's at the model's own eyes ({@link WallSit#eye}); (Andy,
 * 2026-11-20: "it should also follow the rising head as you get up from downed or wall sit") getting up off the floor, at
 * the rising body's eyes ({@link CrawlPose#riseEye}). Stood up from either, vanilla's view (still easing up from lying)
 * is set at the standing eyes at once, so it doesn't dip back down from where the head got to.
 */
@Mixin(Camera.class)
abstract class ProneCameraMixin {
	@Shadow
	private boolean detached;
	@Shadow
	private Entity entity;
	@Shadow
	private float eyeHeight;
	@Shadow
	private float eyeHeightOld;
	/** The view was last put at (near) standing eyes by a get-up here, and vanilla's may still be easing up from lying. */
	@Unique
	private boolean jaymospowers$stoodUp;

	@Shadow
	public abstract Vec3 position();

	@Shadow
	protected abstract void setPosition(Vec3 position);

	/** A get-up's view this high up the standing eyes (or more) counts as stood up. */
	@Unique
	private static final float STOOD_SHARE = 0.8F;

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$proneView(float partialTicks, CallbackInfo ci) {
		if (this.detached || !(this.entity instanceof Player player) || player.isPassenger()) {
			return;
		}
		Vec3 at = this.position();
		Vec3 sat = WallSit.eye(player, partialTicks, at);
		if (sat == null) {
			sat = CrawlPose.riseEye(player, partialTicks, at);
		}
		double feet = Mth.lerp(partialTicks, player.yo, player.getY());
		if (sat != null) {
			// (Andy, 2026-11-15: "the view needs to move to the exact height on the model") downed, sat against a wall, or
			// getting up off the floor
			this.setPosition(sat);
			this.jaymospowers$stoodUp = sat.y - feet >= STOOD_SHARE * player.getEyeHeight(Pose.STANDING);
			return;
		}
		if (this.jaymospowers$stoodUp) {
			if (Downed.isDowned(player)) {
				// dropped back down: vanilla's view never left the floor
				this.jaymospowers$stoodUp = false;
			} else if (player.getPose() == Pose.SWIMMING) {
				// up, the pose not caught up yet: held at the standing eyes
				this.setPosition(new Vec3(at.x, feet + player.getEyeHeight(Pose.STANDING), at.z));
				return;
			} else {
				// vanilla's view eases on from the standing eyes (down to a crouch, if Sneak is still held)
				this.eyeHeight = this.eyeHeightOld = player.getEyeHeight(Pose.STANDING);
				this.jaymospowers$stoodUp = false;
				at = new Vec3(at.x, feet + this.eyeHeight, at.z);
				this.setPosition(at);
			}
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
