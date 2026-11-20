package com.jaymoskingdoms.mixin.forestfire;

import com.jaymoskingdoms.ForestFireCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (ff-pits) Peterwolf's Forest Fire (MIT; only hooked) takes every vanilla fire that ticks into its own fire, and one
 * with nothing it can burn under it (netherrack) it simply removes. A fire kept burning on purpose (fire pits where the
 * dead are burned, village hearths, the Nether's own fires: whatever burns forever here, {@link ForestFireCompat#kept})
 * is left to vanilla, so it never goes out or becomes an incident its crews are sent to. Skipped without it.
 */
@Pseudo
@Mixin(targets = "com.peterwolf.forestfire.fire.simulation.FireSimulation", remap = false)
abstract class ForestFireKeptMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "adoptVanillaFire", at = @At("HEAD"), cancellable = true, remap = false)
	private void jaymoskingdoms$leaveKeptFire(BlockPos firePos, CallbackInfo ci) {
		if (ForestFireCompat.kept(level, firePos)) {
			ci.cancel();
		}
	}
}
