package com.jaymospowers.mixin.tundraworld;

import com.jaymospowers.tundra.world.TundraWorld;
import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The tundra and the lichen woodland take the dry end of Snowy Plains in the Overworld's biome list ({@link TundraWorld#placing}). */
@Mixin(OverworldBiomeBuilder.class)
abstract class TundraPlacementMixin {
	@ModifyVariable(method = "addBiomes", at = @At("HEAD"), argsOnly = true)
	private Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> jaymospowers$tundra(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out) {
		return TundraWorld.placing(out);
	}
}
