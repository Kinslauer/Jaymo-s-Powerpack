package com.jaymoskitchen.herb;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.delight.WildCropBlock;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * (0.5.29) Andy (2026-10-25) wanted Pmeh a rare commodity, its trade worth Jaymo's Kingdoms' speakeasies: it no longer
 * comes from shearing tall grass. It grows wild only in jungles and savannas, a scattered patch here and there
 * ({@code worldgen/placed_feature/wild_pmeh.json}); pick a plant for its leaves (1-3 Pmeh, {@code loot_table/blocks/wild_pmeh.json}).
 * There's no seed and no block item: what grows wild is all there is, so most of it reaches you through the dens.
 */
public final class WildPmeh {
	public static Block WILD_PMEH;

	private WildPmeh() {
	}

	public static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id("wild_pmeh"));
		WILD_PMEH = Registry.register(BuiltInRegistries.BLOCK, key, new WildCropBlock(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED)));
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_JUNGLE).or(BiomeSelectors.tag(BiomeTags.IS_SAVANNA)),
			GenerationStep.Decoration.VEGETAL_DECORATION, ResourceKey.create(Registries.PLACED_FEATURE, JaymosKitchen.id("wild_pmeh")));
	}
}
