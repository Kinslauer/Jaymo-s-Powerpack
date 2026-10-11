package com.jaymospowers.power.darktide.mixin;

import com.jaymospowers.power.darktide.DarktideStatus;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A crippled player crawls (both sides read the synced cripple, so the pose and hitbox agree). */
@Mixin(Player.class)
abstract class DarktidePlayerPoseMixin {
	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$darktideCrawl(CallbackInfo ci) {
		Player self = (Player) (Object) this;
		if (DarktideStatus.crippled(self) && !self.isSpectator() && !self.isPassenger()) {
			self.setPose(Pose.SWIMMING);
			ci.cancel();
		}
	}
}
