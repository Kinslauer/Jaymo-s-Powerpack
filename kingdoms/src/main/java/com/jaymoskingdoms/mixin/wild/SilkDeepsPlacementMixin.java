package com.jaymoskingdoms.mixin.wild;

import com.jaymoskingdoms.wild.nest.SilkDeeps;
import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The silk deeps go into the Overworld's biome list beside the other cave biomes ({@link SilkDeeps#placing}). */
@Mixin(OverworldBiomeBuilder.class)
abstract class SilkDeepsPlacementMixin {
	@ModifyVariable(method = "addBiomes", at = @At("HEAD"), argsOnly = true)
	private Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> jaymoskingdoms$silkDeeps(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out) {
		return SilkDeeps.placing(out);
	}
}
