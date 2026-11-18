package com.jaymospowers.power.chrono.mixin;

import com.jaymospowers.power.chrono.ChronoMomentum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Arrows and every other shot pass through a speedster who is a blur or phasing ({@link ChronoMomentum#shotsPass}). Runs on
 * both sides, so a shot is not seen to stop at one it went through.
 */
@Mixin(Projectile.class)
abstract class ChronoShotPassMixin {
	@Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$shotsPass(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player player && ChronoMomentum.shotsPass(player)) {
			cir.setReturnValue(false);
		}
	}
}
