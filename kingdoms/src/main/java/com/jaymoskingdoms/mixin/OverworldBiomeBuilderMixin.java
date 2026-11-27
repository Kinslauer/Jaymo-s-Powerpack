package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.lands.Lands;
import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Overworld's biome list gets our four lands ({@link Lands}), each at a point no climate reaches (so the game's own choice
 * is never changed by them): they are then among the biome source's possible biomes, which is what a biome needs to have its
 * features placed and its structures checked. {@code MultiNoiseBiomeSourceMixin} puts them on the map.
 */
@Mixin(OverworldBiomeBuilder.class)
abstract class OverworldBiomeBuilderMixin {
	@Inject(method = "addBiomes", at = @At("TAIL"))
	private void jaymoskingdoms$lands(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer, CallbackInfo ci) {
		if (Lands.buildingBuiltIn()) {
			return;
		}
		ResourceKey<Biome>[] all = Lands.all();
		for (int i = 0; i < all.length; i++) {
			consumer.accept(Pair.of(Lands.parked(i), all[i]));
		}
	}
}
