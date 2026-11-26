package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.lands.Lands;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps our lands out of the built-in registries' Overworld biome list while the game builds it ({@link Lands#buildingBuiltIn()}). */
@Mixin(MultiNoiseBiomeSourceParameterLists.class)
abstract class ParameterListsBootstrapMixin {
	@Inject(method = "bootstrap", at = @At("HEAD"))
	private static void jaymoskingdoms$builtInStart(BootstrapContext<MultiNoiseBiomeSourceParameterList> context, CallbackInfo ci) {
		Lands.buildingBuiltIn(true);
	}

	@Inject(method = "bootstrap", at = @At("RETURN"))
	private static void jaymoskingdoms$builtInEnd(BootstrapContext<MultiNoiseBiomeSourceParameterList> context, CallbackInfo ci) {
		Lands.buildingBuiltIn(false);
	}
}
