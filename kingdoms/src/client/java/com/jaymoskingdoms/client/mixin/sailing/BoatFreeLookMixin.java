package com.jaymoskingdoms.client.mixin.sailing;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A free riding camera (Andy's survival picks, "free riding camera"): in a boat or a raft your look is no longer turned
 * with it each tick, so looking around isn't dragged (or juddered) as it swings about (MC-122731, MC-259512; our own
 * fix, from the idea only). You still look no further round than vanilla's 105 degrees off the bow: past that the
 * boat's own clamp turns you. Animals and mobs aboard turn with it as before. A ship's helm does the same
 * ({@code ShipClient#tick}); free rein in the saddle is its own ({@code FreeReinClient}).
 */
@Mixin(AbstractBoat.class)
abstract class BoatFreeLookMixin {
	@Redirect(method = "positionRider", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setYRot(F)V"))
	private void jaymoskingdoms$keepLook(Entity passenger, float yRot) {
		if (!(passenger instanceof Player)) {
			passenger.setYRot(yRot);
		}
	}

	@Redirect(method = "positionRider", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setYHeadRot(F)V"))
	private void jaymoskingdoms$keepHead(Entity passenger, float yHeadRot) {
		if (!(passenger instanceof Player)) {
			passenger.setYHeadRot(yHeadRot);
		}
	}
}
