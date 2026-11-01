package com.jaymospowers.core.wounds.herbs;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.MedicineItems;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * (0.19.20) The five wild herbs (docs/Healing-Plan.md section 1). Andy: "make the herbs common so you have an early method
 * to treat wounds". Each is a {@link HerbBushBlock} with its herb item (which plants it), and a patch feature (data:
 * {@code worldgen/feature/<id>_patch.json}, {@code worldgen/placed_feature/<id>_patch.json}) added to the biomes it grows in,
 * like vanilla flowers. Blocks and items are registered from {@link MedicineItems#register}, so the herbs sit in the
 * Medicine creative tab. Assets: tools/gen_herbs.py.
 */
public final class Herbs {
	/** The herbs' ids, in step with tools/gen_herbs.py and the data files. */
	public static final String[] IDS = {"comfrey", "yarrow", "plantain", "wild_garlic", "chamomile"};

	public static Block COMFREY_BUSH, YARROW_BUSH, PLANTAIN_BUSH, WILD_GARLIC_BUSH, CHAMOMILE_BUSH;
	public static Item COMFREY, YARROW, PLANTAIN, WILD_GARLIC, CHAMOMILE;

	private Herbs() {
	}

	public static void register() {
		COMFREY_BUSH = block("comfrey", () -> COMFREY, MapColor.PLANT);
		YARROW_BUSH = block("yarrow", () -> YARROW, MapColor.SNOW);
		PLANTAIN_BUSH = block("plantain", () -> PLANTAIN, MapColor.PLANT);
		WILD_GARLIC_BUSH = block("wild_garlic", () -> WILD_GARLIC, MapColor.PLANT);
		CHAMOMILE_BUSH = block("chamomile", () -> CHAMOMILE, MapColor.TERRACOTTA_YELLOW);
		COMFREY = herb("comfrey", COMFREY_BUSH);
		YARROW = herb("yarrow", YARROW_BUSH);
		PLANTAIN = herb("plantain", PLANTAIN_BUSH);
		WILD_GARLIC = herb("wild_garlic", WILD_GARLIC_BUSH);
		CHAMOMILE = herb("chamomile", CHAMOMILE_BUSH);

		// where each grows (the patch's own numbers are in its placed feature)
		Predicate<BiomeSelectionContext> land = BiomeSelectors.foundInOverworld()
			.and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate())
			.and(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN).negate())
			.and(BiomeSelectors.tag(BiomeTags.IS_BEACH).negate())
			.and(BiomeSelectors.tag(BiomeTags.IS_BADLANDS).negate());
		Predicate<BiomeSelectionContext> comfrey = BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.FOREST,
			Biomes.BIRCH_FOREST, Biomes.FLOWER_FOREST, Biomes.DAPPLED_FOREST, Biomes.SWAMP).or(BiomeSelectors.tag(BiomeTags.IS_RIVER));
		Predicate<BiomeSelectionContext> yarrow = BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.SAVANNA,
			Biomes.SAVANNA_PLATEAU, Biomes.WINDSWEPT_SAVANNA).or(BiomeSelectors.tag(BiomeTags.IS_HILL));
		Predicate<BiomeSelectionContext> garlic = BiomeSelectors.tag(BiomeTags.IS_FOREST).or(BiomeSelectors.tag(BiomeTags.IS_TAIGA))
			.or(BiomeSelectors.includeByKey(Biomes.DARK_FOREST, Biomes.SWAMP, Biomes.MANGROVE_SWAMP));
		Predicate<BiomeSelectionContext> chamomile = BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW,
			Biomes.FLOWER_FOREST);
		BiomeModifications.addFeature(comfrey, GenerationStep.Decoration.VEGETAL_DECORATION, placed("comfrey_patch"));
		BiomeModifications.addFeature(yarrow, GenerationStep.Decoration.VEGETAL_DECORATION, placed("yarrow_patch"));
		BiomeModifications.addFeature(land, GenerationStep.Decoration.VEGETAL_DECORATION, placed("plantain_patch"));
		BiomeModifications.addFeature(garlic, GenerationStep.Decoration.VEGETAL_DECORATION, placed("wild_garlic_patch"));
		BiomeModifications.addFeature(chamomile, GenerationStep.Decoration.VEGETAL_DECORATION, placed("chamomile_patch"));
	}

	private static Block block(String id, Supplier<Item> herb, MapColor color) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.BLOCK, key, new HerbBushBlock(BlockBehaviour.Properties.of().mapColor(color).noCollision()
			.randomTicks().instabreak().sound(SoundType.SWEET_BERRY_BUSH).pushReaction(PushReaction.POPPED).setId(key), herb));
	}

	private static Item herb(String id, Block bush) {
		return MedicineItems.item(id, p -> new BlockItem(bush, p));
	}

	private static ResourceKey<PlacedFeature> placed(String id) {
		return ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id(id));
	}
}
