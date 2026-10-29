package com.jaymospowers.power.zfighter.mixin;

import com.jaymospowers.power.zfighter.ZFighterPodTrip;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.18.4) No climbing out of a space pod mid-trip ({@link ZFighterPodTrip}): sneaking does nothing until it lands. */
@Mixin(Player.class)
abstract class ZFighterPodRideMixin {
	@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$stayInPod(CallbackInfoReturnable<Boolean> cir) {
		Entity vehicle = ((Player) (Object) this).getVehicle();
		if (vehicle != null && vehicle.entityTags().contains(ZFighterPodTrip.TAG)) {
			cir.setReturnValue(false);
		}
	}
}
