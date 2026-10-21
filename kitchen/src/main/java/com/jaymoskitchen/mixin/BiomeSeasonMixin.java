package com.jaymoskitchen.mixin;

import com.jaymoskitchen.season.Seasons;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.5.4) Temperate lands are colder out of summer, most of all in winter, when they're cold enough to snow and freeze
 * still water: the game's own rain-or-snow and freezing go by this temperature, on both sides.
 */
@Mixin(Biome.class)
abstract class BiomeSeasonMixin {
	@Inject(method = "getTemperature", at = @At("RETURN"), cancellable = true)
	private void jaymoskitchen$seasonChill(BlockPos pos, int seaLevel, CallbackInfoReturnable<Float> cir) {
		float chill = Seasons.chill(((Biome)(Object)this).getBaseTemperature());
		if (chill != 0.0F) {
			cir.setReturnValue(cir.getReturnValueF() + chill);
		}
	}
}
