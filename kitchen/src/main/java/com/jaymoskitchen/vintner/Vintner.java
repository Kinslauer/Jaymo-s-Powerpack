package com.jaymoskitchen.vintner;

import com.jaymoskitchen.JaymosKitchen;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Medieval deco patch 4, drinks and kitchen: grape vines on trellises (wild ones on the edges of plains, savanna and flower
 * forests) and the Nether's crimson and warped vines on its lava shores ({@link GrapeVineBlock}); the treading tub and the
 * apple press ({@link JuiceBlock}), whose juice goes into a cask; wine that carries its year and improves in a cool cellar
 * ({@link Vintage}), sold to lords and merchants ({@link WineTrade}); tea bushes ({@link TeaBushBlock}) whose leaves dry on a
 * drying rack and brew in the kettle, and the tea pot ({@link TeaPotBlock}); and the smoker's wood chips ({@link Chip}).
 */
public final class Vintner {
	public static GrapeVineBlock GRAPE_VINE, CRIMSON_VINE, WARPED_VINE;
	public static TeaBushBlock TEA_BUSH;
	public static JuiceBlock TREADING_TUB, APPLE_PRESS;
	public static TeaPotBlock TEA_POT;
	public static BlockEntityType<JuiceEntity> JUICE_ENTITY;
	public static Item GRAPES, CRIMSON_GRAPES, WARPED_GRAPES;
	public static Item GRAPE_JUICE, APPLE_JUICE, BLACK_JUICE;
	public static Item TEA_LEAVES, DRIED_TEA_LEAVES;
	public static Item GRAPE_VINE_ITEM, CRIMSON_VINE_ITEM, WARPED_VINE_ITEM, TEA_BUSH_ITEM;
	public static Item TREADING_TUB_ITEM, APPLE_PRESS_ITEM, TEA_POT_ITEM;

	private Vintner() {
	}

	public static void register() {
		Vintage.register();
		WineTrade.register();

		GRAPES = food("grapes", 2, 0.2F);
		CRIMSON_GRAPES = item("crimson_grapes", new Item.Properties());
		WARPED_GRAPES = item("warped_grapes", new Item.Properties());
		GRAPE_JUICE = drink("grape_juice");
		APPLE_JUICE = drink("apple_juice");
		BLACK_JUICE = drink("black_juice");
		TEA_LEAVES = item("tea_leaves", new Item.Properties());
		DRIED_TEA_LEAVES = item("dried_tea_leaves", new Item.Properties());
		for (Chip chip : Chip.values()) {
			chip.item(item("wood_chip_" + chip.id, new Item.Properties()));
		}

		BlockBehaviour.Properties plant = BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak()
			.sound(SoundType.CROP).pushReaction(PushReaction.POPPED);
		GRAPE_VINE = block("grape_vine", p -> new GrapeVineBlock(GrapeVineBlock.Kind.GRAPE, p), plant);
		CRIMSON_VINE = block("crimson_vine", p -> new GrapeVineBlock(GrapeVineBlock.Kind.CRIMSON, p), BlockBehaviour.Properties.of()
			.mapColor(MapColor.CRIMSON_STEM).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED));
		WARPED_VINE = block("warped_vine", p -> new GrapeVineBlock(GrapeVineBlock.Kind.WARPED, p), BlockBehaviour.Properties.of()
			.mapColor(MapColor.WARPED_STEM).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED));
		TEA_BUSH = block("tea_bush", TeaBushBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks()
			.instabreak().sound(SoundType.SWEET_BERRY_BUSH).pushReaction(PushReaction.POPPED));
		TREADING_TUB = block("treading_tub", p -> new JuiceBlock(true, p), BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(2.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
		APPLE_PRESS = block("apple_press", p -> new JuiceBlock(false, p), BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(2.5F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
		TEA_POT = block("tea_pot", TeaPotBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.5F)
			.sound(SoundType.DECORATED_POT).noOcclusion());
		GRAPE_VINE_ITEM = blockItem("grape_vine", GRAPE_VINE);
		CRIMSON_VINE_ITEM = blockItem("crimson_vine", CRIMSON_VINE);
		WARPED_VINE_ITEM = blockItem("warped_vine", WARPED_VINE);
		TEA_BUSH_ITEM = blockItem("tea_bush", TEA_BUSH);
		TREADING_TUB_ITEM = blockItem("treading_tub", TREADING_TUB);
		APPLE_PRESS_ITEM = blockItem("apple_press", APPLE_PRESS);
		TEA_POT_ITEM = blockItem("tea_pot", TEA_POT);

		JUICE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("juice_press"),
			new BlockEntityType<>(JuiceEntity::new, Set.of(TREADING_TUB, APPLE_PRESS)));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			for (Item item : List.of(GRAPE_VINE_ITEM, CRIMSON_VINE_ITEM, WARPED_VINE_ITEM, TEA_BUSH_ITEM)) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			for (Item item : List.of(TREADING_TUB_ITEM, APPLE_PRESS_ITEM, TEA_POT_ITEM)) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			for (Item item : List.of(GRAPES, CRIMSON_GRAPES, WARPED_GRAPES, GRAPE_JUICE, APPLE_JUICE, BLACK_JUICE, TEA_LEAVES, DRIED_TEA_LEAVES)) {
				output.accept(new ItemStack(item));
			}
			for (Chip chip : Chip.values()) {
				output.accept(new ItemStack(chip.item()));
			}
		});

		// wild vines at the edges of the warm plains, wild tea in the misty and cherry lands, the Nether's vines on its lava shores
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.SAVANNA, Biomes.FLOWER_FOREST),
			GenerationStep.Decoration.VEGETAL_DECORATION, ResourceKey.create(Registries.PLACED_FEATURE, JaymosKitchen.id("wild_grape_vine")));
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.CHERRY_GROVE, Biomes.WINDSWEPT_HILLS,
			Biomes.WINDSWEPT_FOREST), GenerationStep.Decoration.VEGETAL_DECORATION,
			ResourceKey.create(Registries.PLACED_FEATURE, JaymosKitchen.id("wild_tea_bush")));
		BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(), GenerationStep.Decoration.VEGETAL_DECORATION,
			ResourceKey.create(Registries.PLACED_FEATURE, JaymosKitchen.id("wild_crimson_vine")));
		BiomeModifications.addFeature(BiomeSelectors.foundInTheNether(), GenerationStep.Decoration.VEGETAL_DECORATION,
			ResourceKey.create(Registries.PLACED_FEATURE, JaymosKitchen.id("wild_warped_vine")));
	}

	/** Ripe in the sun: fresh tea leaves on a rack dry only while the sun is on them. */
	public static boolean sunny(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
		return level.canSeeSky(pos.above()) && level.getOverworldClockTime() % 24000L < 12000L;
	}

	private static Item item(String id, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
	}

	private static Item food(String id, int nutrition, float saturation) {
		return item(id, new Item.Properties().food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build()));
	}

	/** A bottled juice: drunk like the fermented drinks, and leaves its glass bottle. */
	private static Item drink(String id) {
		Item.Properties properties = new Item.Properties();
		properties.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.2F).build(), Consumables.DEFAULT_DRINK);
		properties.usingConvertsTo(Items.GLASS_BOTTLE).stacksTo(16);
		return item(id, properties);
	}

	private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(key)));
	}
}
