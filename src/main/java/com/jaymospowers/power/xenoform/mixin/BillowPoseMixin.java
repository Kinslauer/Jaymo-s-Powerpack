package com.jaymospowers.power.xenoform.mixin;

import com.jaymospowers.power.xenoform.forms.Billow;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.19.29) Billow is a cloud, low and loose like the Smoke power's wholly-smoke: it takes the swimming pose's box
 * (a block high at its size), so it slips through holes and under things. It's drawn upright all the same
 * ({@code XenoformClient.extract}).
 */
@Mixin(Player.class)
abstract class BillowPoseMixin {
	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$billowLow(CallbackInfo ci) {
		Player self = (Player)(Object)this;
		if (Billow.cloud(self)) {
			self.setPose(Pose.SWIMMING);
			ci.cancel();
		}
	}
}
