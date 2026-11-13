package com.jaymoskingdoms.mixin.occult;

import com.jaymoskingdoms.LivingNightmare;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No phantom is ever put in the sky: the sleepless see theirs alone instead ({@link LivingNightmare#sleepless}). */
@Mixin(PhantomSpawner.class)
abstract class PhantomSpawnerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$nightmaresOnly(ServerLevel level, boolean spawnEnemies, CallbackInfo ci) {
		if (spawnEnemies) {
			LivingNightmare.sleepless(level);
		}
		ci.cancel();
	}
}
