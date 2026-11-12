package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.sailing.Berths;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** One asleep in a berth aboard a ship under sail ({@link Berths}) has a bed, though it isn't in the world while she sails. */
@Mixin(LivingEntity.class)
abstract class LivingBerthMixin {
	@Inject(method = "checkBedExists", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$berthAboard(CallbackInfoReturnable<Boolean> cir) {
		if ((Object)this instanceof ServerPlayer player && Berths.sleepingAboard(player)) {
			cir.setReturnValue(true);
		}
	}
}
