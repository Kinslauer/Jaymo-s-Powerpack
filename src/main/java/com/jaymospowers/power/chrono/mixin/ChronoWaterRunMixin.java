package com.jaymospowers.power.chrono.mixin;

import com.jaymospowers.power.chrono.ChronoSpeed;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.10.28) Overclock's water run: a sprinting, overclocked player stands on water the way a strider stands on lava
 * (the surface of still water turns solid underfoot, on both sides). Stop sprinting and you sink.
 */
@Mixin(LivingEntity.class)
abstract class ChronoWaterRunMixin {
	@Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$chronoWaterRun(FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player player && ChronoSpeed.runsOn(player, fluid)) {
			cir.setReturnValue(true);
		}
	}
}
