package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.seas.SeaLife;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The sea's own spawns come thin along the coast and thick far out ({@link SeaLife}). */
@Mixin(NaturalSpawner.class)
abstract class SeaLifeSpawnMixin {
	@Inject(method = "isValidPositionForMob", at = @At("RETURN"), cancellable = true)
	private static void jaymoskingdoms$seaLifeFarOut(ServerLevel level, Mob mob, double nearestPlayerDistanceSqr, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && !SeaLife.keep(level, mob)) {
			cir.setReturnValue(false);
		}
	}
}
