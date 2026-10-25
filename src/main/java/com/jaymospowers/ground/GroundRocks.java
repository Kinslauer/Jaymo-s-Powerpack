package com.jaymospowers.ground;

import com.jaymospowers.JaymosPowers;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * (0.16.3) Andy: "the world should be populated with random rocks [...] and flint". Loose rocks lie about the overworld,
 * in the stone of the ground under them (sandstone in deserts, deepslate down deep, mossy in jungles and swamps...), up
 * to 4 in a block like candles; loose flint lies about too, most on gravel and by water. A punch or a right click picks
 * them up; 4 rocks craft into their block (stone rocks into cobblestone). Rocks that lie in water are salt crystals
 * (0.16.24). Assets: tools/gen_ground_rocks.py.
 */
public final class GroundRocks {
	/** The rock kinds. Keep in step with VARIANTS in tools/gen_ground_rocks.py. */
	public enum Variant {
		STONE, MOSSY, GRANITE, DIORITE, ANDESITE, DEEPSLATE, TUFF, SANDSTONE, RED_SANDSTONE;

		public String id() {
			return this.name().toLowerCase(java.util.Locale.ROOT) + "_rock";
		}
	}

	public static final Map<Variant, Block> ROCKS = new EnumMap<>(Variant.class);
	public static Block LOOSE_FLINT;
	/** (0.16.24) Andy: "the rocks that spawn in water are salt crystals". They give Kitchen's salt (for tanning hides). */
	public static Block SALT_CRYSTALS;

	private GroundRocks() {
	}

	public static void init() {
		for (Variant variant : Variant.values()) {
			ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(variant.id()));
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(variant.id()));
			Block block = Registry.register(BuiltInRegistries.BLOCK, key,
				new LooseStonesBlock.Rocks(stones(variant == Variant.SANDSTONE || variant == Variant.RED_SANDSTONE ? MapColor.SAND : MapColor.STONE)
					.setId(key), () -> BuiltInRegistries.ITEM.getValue(itemKey)));
			Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
			ROCKS.put(variant, block);
		}
		ResourceKey<Block> flintKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("loose_flint"));
		LOOSE_FLINT = Registry.register(BuiltInRegistries.BLOCK, flintKey,
			new LooseStonesBlock.Flint(stones(MapColor.COLOR_GRAY).setId(flintKey), () -> Items.FLINT));
		ResourceKey<Block> saltKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("salt_crystals"));
		Identifier salt = Identifier.fromNamespaceAndPath("jaymoskitchen", "salt");
		SALT_CRYSTALS = Registry.register(BuiltInRegistries.BLOCK, saltKey,
			new LooseStonesBlock.Salt(stones(MapColor.SNOW).setId(saltKey), () -> BuiltInRegistries.ITEM.getValue(salt)));

		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("loose_rocks"), GroundRocksFeature.ROCKS_CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("loose_flint"), GroundRocksFeature.FLINT_CODEC);
		var land = BiomeSelectors.foundInOverworld()
			.and(BiomeSelectors.tag(BiomeTags.IS_OCEAN).negate())
			.and(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN).negate());
		BiomeModifications.addFeature(land, GenerationStep.Decoration.VEGETAL_DECORATION, placed("loose_rocks"));
		BiomeModifications.addFeature(land, GenerationStep.Decoration.VEGETAL_DECORATION, placed("loose_flint"));
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_DECORATION, placed("loose_rocks_cave"));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			for (Block rock : ROCKS.values()) {
				output.accept(rock);
			}
		});
	}

	private static BlockBehaviour.Properties stones(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).instabreak().noCollision().noOcclusion().sound(SoundType.STONE)
			.pushReaction(PushReaction.POPPED).offsetType(BlockBehaviour.OffsetType.XZ);
	}

	private static ResourceKey<PlacedFeature> placed(String id) {
		return ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id(id));
	}
}
