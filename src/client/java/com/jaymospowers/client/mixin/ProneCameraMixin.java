package com.jaymospowers.client.mixin;

import com.jaymospowers.client.CrawlPose;
import com.jaymospowers.client.ProneTransition;
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
 * (0.19.1) Prone, in first person: the view goes down (and up) with the head as you crouch into the crawl or get up from
 * it ({@link ProneTransition}), and follows the head round when you turn over onto your back ({@link CrawlPose}).
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
