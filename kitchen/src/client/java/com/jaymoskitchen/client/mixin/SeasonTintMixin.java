package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.client.season.SeasonTints;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.5.4) Seasonal leaves and grass in temperate lands (see {@link SeasonTints}). */
@Mixin(Biome.class)
abstract class SeasonTintMixin {
	@Inject(method = "getFoliageColor", at = @At("RETURN"), cancellable = true)
	private void jaymoskitchen$seasonLeaves(CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(SeasonTints.foliage(((Biome)(Object)this).getBaseTemperature(), cir.getReturnValueI()));
	}

	@Inject(method = "getGrassColor", at = @At("RETURN"), cancellable = true)
	private void jaymoskitchen$seasonGrass(double x, double z, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(SeasonTints.grass(((Biome)(Object)this).getBaseTemperature(), cir.getReturnValueI()));
	}
}
