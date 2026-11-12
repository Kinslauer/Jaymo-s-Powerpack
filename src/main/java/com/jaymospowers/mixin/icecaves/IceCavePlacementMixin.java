package com.jaymospowers.mixin.icecaves;

import com.jaymospowers.frost.IceCaves;
import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The ice caves go into the Overworld's biome list beside the other cave biomes ({@link IceCaves#placing}). */
@Mixin(OverworldBiomeBuilder.class)
abstract class IceCavePlacementMixin {
	@ModifyVariable(method = "addBiomes", at = @At("HEAD"), argsOnly = true)
	private Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> jaymospowers$iceCaves(Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> out) {
		return IceCaves.placing(out);
	}
}
