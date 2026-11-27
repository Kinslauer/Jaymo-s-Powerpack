package com.jaymospowers.client.mixin;

import com.jaymospowers.client.moving.BodyWeight;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In first person your view sinks with your body as a landing squashes it (see BodyWeight); the field of view is never touched. */
@Mixin(Camera.class)
abstract class BodyWeightCameraMixin {
	@Shadow
	private boolean detached;
	@Shadow
	private Entity entity;

	@Shadow
	public abstract Vec3 position();

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void jaymospowers$landingSink(float partialTicks, CallbackInfo ci) {
		if (this.detached || !(this.entity instanceof Player player)) {
			return;
		}
		float sink = BodyWeight.viewSink(player, partialTicks);
		if (sink != 0.0F) {
			this.setPosition(this.position().add(0.0, -sink, 0.0));
		}
	}
}
