package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Attention;
import net.conczin.mca.entity.VillagerEntityMCA;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.281) MCA teleports a villager to its walk target when the way there fails ({@code WanderOrTeleportToTargetTask}),
 * and a mounted one with its mount. Not while a player has them in their menu, or dealt with them in the last 30 seconds
 * ({@link Attention#held}).
 */
@Mixin(VillagerEntityMCA.class)
abstract class McaTeleportMixin {
	@Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$held(double x, double y, double z, CallbackInfo ci) {
		VillagerEntityMCA villager = (VillagerEntityMCA)(Object)this;
		if (villager.getInteractions().getInteractingPlayer().isPresent()) {
			Attention.heed(villager);
		}
		if (Attention.held(villager)) {
			ci.cancel();
		}
	}
}
