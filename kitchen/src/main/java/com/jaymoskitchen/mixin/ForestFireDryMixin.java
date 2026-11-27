package com.jaymoskitchen.mixin;

import com.jaymoskitchen.season.Tropics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forest Fire's wildfires read the hot lands' dry season too: its one measure of how damp the air is
 * ({@code WeatherFireModel.humidity01}, which its spread, embers, incidents and fuel moisture all read) drops to
 * {@link Tropics#DRY_HUMIDITY} of itself in the dry tropics, so a spark runs further. Skipped without Forest Fire.
 */
@Pseudo
@Mixin(targets = "com.peterwolf.forestfire.fire.weather.WeatherFireModel", remap = false)
abstract class ForestFireDryMixin {
	@Inject(method = "humidity01(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)F",
		at = @At("RETURN"), cancellable = true, require = 0)
	private static void jaymoskitchen$dryAir(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
		if (Tropics.dryAt(level, pos)) {
			cir.setReturnValue(cir.getReturnValueF() * Tropics.DRY_HUMIDITY);
		}
	}
}
