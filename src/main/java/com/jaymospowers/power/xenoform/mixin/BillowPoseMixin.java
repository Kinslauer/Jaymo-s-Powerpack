package com.jaymospowers.power.xenoform.mixin;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.19.34) Billow is a cloud, low and loose like the Smoke power's wholly-smoke: it takes the swimming pose's box
 * (a block high at its size), so it slips through holes and under things. It's drawn upright all the same
 * ({@code XenoformClient.extract}). (0.19.66) So does Croaker, a squat frog ({@code XenoformState.LOW_BOX}).
 */
@Mixin(Player.class)
abstract class BillowPoseMixin {
	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$billowLow(CallbackInfo ci) {
		Player self = (Player)(Object)this;
		if (com.jaymospowers.power.xenoform.XenoformState.lowBox(self)) {
			self.setPose(Pose.SWIMMING);
			ci.cancel();
			return;
		}
		// (0.20.23) Wisp's and Revenant's float stays upright: sneaking (to sink) mustn't crouch it mid-air
		if (com.jaymospowers.power.xenoform.XenoformState.flight(self) == com.jaymospowers.power.xenoform.XenoformState.FLY_FLOAT) {
			self.setPose(Pose.STANDING);
			ci.cancel();
		}
	}
}
