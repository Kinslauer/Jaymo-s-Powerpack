package com.jaymoskitchen.delight;

import com.jaymoskitchen.JaymosKitchen;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.item.v1.BlockTransformerHelper;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * (0.5.33) Farmer's Delight's farm (MIT, by vectorwing; see LICENSE_farmersdelight) in the Kitchen, with its models and
 * art: tomatoes (seeds, the budding bush, the vine, and the vine climbing a rope) and onions, the rotten tomato to
 * throw, wild onions, tomatoes, carrots, potatoes and beetroots (with sandy shrubs on the beaches), rope and rope
 * fences, organic compost that rots down into rich soil (and rich soil farmland), mushroom colonies and tree bark.
 * The villages get compost heaps and gardens ({@link DelightVillages}).
 */
public final class DelightFarm {
	/** Nutrition and saturation of a tomato and an onion (as Farmer's Delight's). */
	private static final int TOMATO_NUTRITION = 1, ONION_NUTRITION = 2;
	private static final float TOMATO_SATURATION = 0.3F, ONION_SATURATION = 0.4F;
	/** Rotten tomatoes stack to this many. */
	private static final int ROTTEN_TOMATO_STACK = 16;
	/** Flammability (burn chance and spread chance) as vanilla's carpets (rope) and fences. */
	private static final int ROPE_BURN = 60, ROPE_SPREAD = 20, FENCE_BURN = 5, FENCE_SPREAD = 20;

	/** What a compost heap rots quicker beside (mushrooms, mycelium, podzol, rich soil). */
	public static final TagKey<Block> COMPOST_ACTIVATORS = blockTag("compost_activators");
	/** Where mushroom colonies thrive and grow (rich soil, and what the pack adds). */
	public static final TagKey<Block> GROWS_MUSHROOM_COLONIES = blockTag("grows_mushroom_colonies");
	/** Where wild crops can stand. */
	public static final TagKey<Block> GROWS_WILD_CROPS = blockTag("grows_wild_crops");
	/** Plants rich soil boosts from the soil above them (cave vines hang down). */
	public static final TagKey<Block> PLANTED_FROM_BELOW = blockTag("planted_from_below");
	/** Plants rich soil leaves alone (grass, mushrooms, the wild crops). */
	public static final TagKey<Block> UNAFFECTED_BY_RICH_SOIL = blockTag("unaffected_by_rich_soil");
	/** What a tomato vine can climb (the rope, and ropes other mods add). */
	public static final TagKey<Block> ROPES = blockTag("ropes");

	public static BuddingTomatoBlock BUDDING_TOMATOES;
	public static TomatoBlock TOMATOES;
	public static HangingTomatoBlock TOMATOES_ON_ROPE;
	public static OnionCropBlock ONIONS;
	public static RopeBlock ROPE;
	public static RopeFenceBlock ROPE_FENCE;
	public static RopeFenceGateBlock ROPE_FENCE_GATE;
	public static OrganicCompostBlock ORGANIC_COMPOST;
	public static RichSoilBlock RICH_SOIL;
	public static RichSoilFarmlandBlock RICH_SOIL_FARMLAND;
	public static MushroomColonyBlock BROWN_MUSHROOM_COLONY, RED_MUSHROOM_COLONY;
	public static SandyShrubBlock SANDY_SHRUB;
	public static WildPlantBlock WILD_ONIONS, WILD_TOMATOES, WILD_CARROTS, WILD_POTATOES, WILD_BEETROOTS;

	public static Item TOMATO, ONION, TOMATO_SEEDS, ROTTEN_TOMATO, TREE_BARK;
	public static Item ROPE_ITEM;

	public static EntityType<RottenTomatoEntity> ROTTEN_TOMATO_ENTITY;
	/** The splat of a rotten tomato (a squelch, from the slime block's own sounds). */
	public static final SoundEvent ROTTEN_TOMATO_HIT = SoundEvents.SLIME_BLOCK_HIT;

	/** Everything for the creative tabs, in order. */
	private static final List<Item> NATURAL_TAB = new ArrayList<>();
	private static final List<Item> BUILDING_TAB = new ArrayList<>();

	private DelightFarm() {
	}

	public static void register() {
		// the soil first: rich soil farmland is made from it
		RICH_SOIL = block("rich_soil", RichSoilBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT).randomTicks());
		RICH_SOIL_FARMLAND = block("rich_soil_farmland", RichSoilFarmlandBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.FARMLAND));
		ORGANIC_COMPOST = block("organic_compost", OrganicCompostBlock::new,
			BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT).strength(1.2F).sound(SoundType.CROP));
		BROWN_MUSHROOM_COLONY = block("brown_mushroom_colony", p -> new MushroomColonyBlock(Items.BROWN_MUSHROOM.builtInRegistryHolder(), p),
			BlockBehaviour.Properties.ofFullCopy(Blocks.BROWN_MUSHROOM));
		RED_MUSHROOM_COLONY = block("red_mushroom_colony", p -> new MushroomColonyBlock(Items.RED_MUSHROOM.builtInRegistryHolder(), p),
			BlockBehaviour.Properties.ofFullCopy(Blocks.RED_MUSHROOM));

		ROPE = block("rope", RopeBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.CARPET.brown()).noCollision().noOcclusion()
			.strength(0.2F).sound(SoundType.WOOL));
		ROPE_FENCE = block("rope_fence", RopeFenceBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE).strength(1.0F));
		ROPE_FENCE_GATE = block("rope_fence_gate", RopeFenceGateBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_FENCE).strength(1.0F));

		BUDDING_TOMATOES = block("budding_tomatoes", BuddingTomatoBlock::new, crop());
		TOMATOES = block("tomatoes", TomatoBlock::new, crop());
		TOMATOES_ON_ROPE = block("tomatoes_on_rope", HangingTomatoBlock::new, crop().pushReaction(PushReaction.PUSH_PULL));
		ONIONS = block("onions", OnionCropBlock::new, crop());

		SANDY_SHRUB = block("sandy_shrub", SandyShrubBlock::new, wild().replaceable());
		WILD_ONIONS = block("wild_onions", p -> new WildPlantBlock(MobEffects.FIRE_RESISTANCE, 6, p), wild());
		WILD_TOMATOES = block("wild_tomatoes", p -> new WildPlantBlock(MobEffects.POISON, 10, p), wild());
		WILD_CARROTS = block("wild_carrots", p -> new WildPlantBlock(MobEffects.MINING_FATIGUE, 6, p), wild());
		WILD_POTATOES = block("wild_potatoes", p -> new WildPlantBlock(MobEffects.NAUSEA, 8, p), wild());
		WILD_BEETROOTS = block("wild_beetroots", p -> new WildPlantBlock(MobEffects.WATER_BREATHING, 8, p), wild());

		ResourceKey<EntityType<?>> tomatoKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("rotten_tomato"));
		ROTTEN_TOMATO_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, tomatoKey,
			EntityType.Builder.<RottenTomatoEntity>of(RottenTomatoEntity::new, MobCategory.MISC)
				.noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build(tomatoKey));

		// the crops (the onion plants itself, and the tomato seeds plant the budding bush, and stand in for the vine too)
		TOMATO = item("tomato", Item::new, new Item.Properties().food(food(TOMATO_NUTRITION, TOMATO_SATURATION)).villagerFood(1)
			.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
		ONION = item("onion", p -> new BlockItem(ONIONS, p), new Item.Properties().useItemDescriptionPrefix()
			.food(food(ONION_NUTRITION, ONION_SATURATION)).villagerFood(1).compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
		TOMATO_SEEDS = item("tomato_seeds", p -> new BlockItem(BUDDING_TOMATOES, p) {
			@Override
			public void registerBlocks(Map<Block, Item> blocks, Item item) {
				super.registerBlocks(blocks, item);
				blocks.put(TOMATOES, item);
			}
		}, new Item.Properties().useItemDescriptionPrefix().compostable(ContextIntProviders.COMPOSTABLE_LOW));
		ROTTEN_TOMATO = item("rotten_tomato", RottenTomatoItem::new, new Item.Properties().stacksTo(ROTTEN_TOMATO_STACK)
			.compostable(ContextIntProviders.COMPOSTABLE_MEDIUM_HIGH));
		TREE_BARK = item("tree_bark", Item::new, new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_LOW)
			.cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_ITEMS_SMALL));

		// blocks with items
		ROPE_ITEM = item("rope", p -> new RopeItem(ROPE, p), new Item.Properties());
		Item ropeFence = blockItem("rope_fence", ROPE_FENCE, ContextIntProviders.COMPOSTABLE_LOW);
		Item ropeFenceGate = blockItem("rope_fence_gate", ROPE_FENCE_GATE, ContextIntProviders.COMPOSTABLE_LOW);
		BUILDING_TAB.add(ropeFence);
		BUILDING_TAB.add(ropeFenceGate);

		NATURAL_TAB.add(TOMATO_SEEDS);
		NATURAL_TAB.add(blockItem("wild_onions", WILD_ONIONS, ContextIntProviders.COMPOSTABLE_MEDIUM));
		NATURAL_TAB.add(blockItem("wild_tomatoes", WILD_TOMATOES, ContextIntProviders.COMPOSTABLE_MEDIUM));
		NATURAL_TAB.add(blockItem("wild_carrots", WILD_CARROTS, ContextIntProviders.COMPOSTABLE_MEDIUM));
		NATURAL_TAB.add(blockItem("wild_potatoes", WILD_POTATOES, ContextIntProviders.COMPOSTABLE_MEDIUM));
		NATURAL_TAB.add(blockItem("wild_beetroots", WILD_BEETROOTS, ContextIntProviders.COMPOSTABLE_MEDIUM));
		NATURAL_TAB.add(blockItem("sandy_shrub", SANDY_SHRUB, ContextIntProviders.COMPOSTABLE_LOW));
		NATURAL_TAB.add(item("brown_mushroom_colony", p -> new MushroomColonyItem(BROWN_MUSHROOM_COLONY, p),
			new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_ALWAYS_ADD_ONE)));
		NATURAL_TAB.add(item("red_mushroom_colony", p -> new MushroomColonyItem(RED_MUSHROOM_COLONY, p),
			new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_ALWAYS_ADD_ONE)));
		NATURAL_TAB.add(blockItem("organic_compost", ORGANIC_COMPOST, null));
		NATURAL_TAB.add(blockItem("rich_soil", RICH_SOIL, null));
		NATURAL_TAB.add(blockItem("rich_soil_farmland", RICH_SOIL_FARMLAND, null));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			for (Item item : NATURAL_TAB) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(output -> {
			for (Item item : BUILDING_TAB) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(new ItemStack(ROPE_ITEM)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.accept(new ItemStack(TOMATO));
			output.accept(new ItemStack(ONION));
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(new ItemStack(ROTTEN_TOMATO));
			output.accept(new ItemStack(TREE_BARK));
		});

		// a hoe turns rich soil into rich soil farmland (the Kitchen's fertile farmland is made on its own)
		BlockTransformerHelper.registerTilling(RICH_SOIL, RICH_SOIL_FARMLAND);

		FlammableBlockRegistry.getDefaultInstance().add(ROPE, ROPE_BURN, ROPE_SPREAD);
		FlammableBlockRegistry.getDefaultInstance().add(ROPE_FENCE, FENCE_BURN, FENCE_SPREAD);
		FlammableBlockRegistry.getDefaultInstance().add(ROPE_FENCE_GATE, FENCE_BURN, FENCE_SPREAD);

		registerWorldgen();
		TreeBark.register();
		DelightVillages.register();
	}

	/**
	 * Wild crops where Farmer's Delight puts them (data/jaymoskitchen/worldgen): onions and carrots over the whole
	 * overworld but the mushroom islands and lush caves, potatoes and tomatoes above ground only (tomatoes in the hot
	 * lands), beetroots and sandy shrubs on the beaches, mushroom colonies on the mushroom islands.
	 */
	private static void registerWorldgen() {
		Predicate<BiomeSelectionContext> overworld = BiomeSelectors.foundInOverworld();
		Predicate<BiomeSelectionContext> aboveGround = overworld.and(BiomeSelectors.tag(ConventionalBiomeTags.IS_UNDERGROUND).negate())
			.and(BiomeSelectors.excludeByKey(Biomes.LUSH_CAVES, Biomes.DRIPSTONE_CAVES, Biomes.DEEP_DARK));
		Predicate<BiomeSelectionContext> notMushroom = overworld.and(BiomeSelectors.tag(ConventionalBiomeTags.IS_MUSHROOM).negate())
			.and(BiomeSelectors.excludeByKey(Biomes.LUSH_CAVES, Biomes.MUSHROOM_FIELDS));
		Predicate<BiomeSelectionContext> hot = BiomeSelectors.tag(ConventionalBiomeTags.IS_HOT_OVERWORLD).or(BiomeSelectors.includeByKey(
			Biomes.DESERT, Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.SAVANNA, Biomes.SAVANNA_PLATEAU, Biomes.STONY_PEAKS, Biomes.WARM_OCEAN,
			Biomes.WINDSWEPT_SAVANNA, Biomes.ERODED_BADLANDS, Biomes.BAMBOO_JUNGLE, Biomes.MANGROVE_SWAMP));
		Predicate<BiomeSelectionContext> beach = BiomeSelectors.tag(ConventionalBiomeTags.IS_BEACH).or(BiomeSelectors.includeByKey(Biomes.BEACH));
		Predicate<BiomeSelectionContext> mushroom = BiomeSelectors.tag(ConventionalBiomeTags.IS_MUSHROOM).or(BiomeSelectors.includeByKey(Biomes.MUSHROOM_FIELDS));

		patch(notMushroom, "wild_onions");
		patch(notMushroom, "wild_carrots");
		patch(aboveGround, "wild_potatoes");
		patch(aboveGround.and(hot), "wild_tomatoes");
		patch(beach, "wild_beetroots");
		patch(mushroom, "brown_mushroom_colony");
		patch(mushroom, "red_mushroom_colony");
	}

	private static void patch(Predicate<BiomeSelectionContext> biomes, String id) {
		BiomeModifications.addFeature(biomes, GenerationStep.Decoration.VEGETAL_DECORATION,
			ResourceKey.create(Registries.PLACED_FEATURE, JaymosKitchen.id(id)));
	}

	/** As vanilla's wheat: no collision, broken at a touch, ticks to grow, pushed pistons break it. */
	private static BlockBehaviour.Properties crop() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak()
			.sound(SoundType.CROP).pushReaction(PushReaction.POPPED);
	}

	/** A wild plant: no collision, broken at a touch. */
	private static BlockBehaviour.Properties wild() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS)
			.pushReaction(PushReaction.POPPED);
	}

	private static FoodProperties food(int nutrition, float saturation) {
		return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
	}

	private static TagKey<Block> blockTag(String id) {
		return TagKey.create(Registries.BLOCK, JaymosKitchen.id(id));
	}

	private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
		if (item instanceof BlockItem blockItem) {
			blockItem.registerBlocks(Item.BY_BLOCK, item);
		}
		return item;
	}

	/** The item that sets a block down (compostable as given, or not at all if null). */
	private static Item blockItem(String id, Block block, ResourceKey<ContextIntProvider> compost) {
		Item.Properties properties = new Item.Properties();
		if (compost != null) {
			properties.compostable(compost);
		}
		return item(id, p -> new BlockItem(block, p), properties);
	}
}
