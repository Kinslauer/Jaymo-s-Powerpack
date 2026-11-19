package com.jaymospowers.mixin;

import com.jaymospowers.core.MountClimb;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player climbs onto a horse, donkey or mule before riding it ({@link MountClimb}). */
@Mixin(AbstractHorse.class)
abstract class MountClimbHorseMixin {
	@Inject(method = "doPlayerRide", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$climbOn(Player player, CallbackInfo ci) {
		if (MountClimb.begin(player, (AbstractHorse)(Object)this)) {
			ci.cancel();
		}
	}
}
