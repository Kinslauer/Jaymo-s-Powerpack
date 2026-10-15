package com.jaymospowers.power.smoke.mixin;

import com.jaymospowers.power.smoke.SmokeVapour;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wholly smoke takes up little room (the swimming pose's box), so it slips through holes a block high. */
@Mixin(Player.class)
abstract class SmokePoseMixin {
	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$smokeWholly(CallbackInfo ci) {
		Player self = (Player)(Object)this;
		if (SmokeVapour.wholly(self)) {
			self.setPose(Pose.SWIMMING);
			ci.cancel();
		}
	}
}
