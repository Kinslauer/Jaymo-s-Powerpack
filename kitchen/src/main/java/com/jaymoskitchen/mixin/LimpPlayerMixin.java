package com.jaymoskitchen.mixin;

import com.jaymoskitchen.ragdoll.LivingRagdolls;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.5.1) A limp player lies flat (the swimming pose, a 0.6 tall box) whatever else they were doing. */
@Mixin(Player.class)
abstract class LimpPlayerMixin {
	@Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$limp(CallbackInfo ci) {
		Player self = (Player)(Object)this;
		if (LivingRagdolls.isLimp(self)) {
			self.setPose(Pose.SWIMMING);
			ci.cancel();
		}
	}
}
