package com.jaymoskitchen.delight;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Quality;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.UseRemainder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.HayBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * (0.5.28) Farmer's Delight (MIT, by vectorwing; see LICENSE_farmersdelight) remade for the Kitchen, with its models and
 * art: the Cooking Pot and Frying Pan set down over a fire ({@link CookwareBlock}), the cutting board and knives
 * ({@link CuttingBoardBlock}, {@link Chopping}), feasts ({@link Feast}), Comfort and Nourishment, and cabbages and
 * rice (with wild cabbages on the coasts and wild rice in the swamps).
 */
public final class Delight {
	/** Comfort from a bowl of something hot: keeps the cold off ({@code BodyHeat}). Doubled for a Perfect one. */
	static final int COMFORT_TICKS = 3 * 60 * 20;
	/**
	 * Nourishment from a rich meal (at least {@code NOURISHING_FROM} nutrition): your meals don't wear off while it
	 * lasts ({@code Stomach}), {@code NOURISHMENT_PER_POINT} ticks for each point from there.
	 */
	static final int NOURISHING_FROM = 11;
	static final int NOURISHMENT_PER_POINT = 60 * 20;
	/** Straw from a grown wheat cut with a knife. */
	static final int WHEAT_STRAW = 1;

	/** What heats cookware set on it (if it can be lit, only lit). */
	public static final TagKey<net.minecraft.world.level.block.Block> HEAT_SOURCES = TagKey.create(Registries.BLOCK, JaymosKitchen.id("heat_sources"));
	/** What cuts on the cutting board (and harvests straw from wheat). */
	public static final TagKey<Item> KNIVES = TagKey.create(Registries.ITEM, JaymosKitchen.id("knives"));

	public static CookwareBlock COOKING_POT_BLOCK;
	public static CookwareBlock FRYING_PAN_BLOCK;
	public static CuttingBoardBlock CUTTING_BOARD;
	public static BlockEntityType<CuttingBoardEntity> CUTTING_BOARD_ENTITY;
	public static CabbageCropBlock CABBAGES;
	public static RiceBlock RICE_CROP;
	public static RicePaniclesBlock RICE_PANICLES;
	public static WildCropBlock WILD_CABBAGES;
	public static Block STRAW_BALE;

	public static Item CABBAGE, CABBAGE_LEAF, CABBAGE_SEEDS, RICE, RICE_PANICLE, STRAW;
	public static Item MINCED_BEEF, BEEF_PATTY, RAW_BACON, CHICKEN_CUTS, COOKED_CHICKEN_CUTS, MUTTON_CHOPS, COOKED_MUTTON_CHOPS,
		COD_SLICE, COOKED_COD_SLICE, SALMON_SLICE, COOKED_SALMON_SLICE;
	public static Item HONEY_GLAZED_HAM, STUFFED_PUMPKIN;
	public static Item IRON_KNIFE;

	public static Holder<MobEffect> COMFORT;
	public static Holder<MobEffect> NOURISHMENT;

	/** Everything for the creative tabs, in order. */
	private static final List<Item> FOOD_TAB = new ArrayList<>();
	private static final List<Item> FEASTS = new ArrayList<>();

	private Delight() {
	}

	/** The cookware blocks: before KitchenBlocks makes the Cooking Pot and Frying Pan items that place them. */
	public static void registerCookware() {
		BlockBehaviour.Properties metal = BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.5F, 6.0F)
			.sound(SoundType.LANTERN).noOcclusion().pushReaction(PushReaction.POPPED);
		COOKING_POT_BLOCK = block("cooking_pot", p -> new CookwareBlock(p, true), metal);
		FRYING_PAN_BLOCK = block("frying_pan", p -> new CookwareBlock(p, false), metal);
	}

	/** A feast's block and the item that sets it down (from ModItems, for the feast dishes). */
	public static Item feast(Dish dish, ResourceKey<Item> key) {
		Feast feast = Feast.of(dish);
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(0.5F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.POPPED);
		Block block;
		if (feast == null) {
			// (0.5.29) the rice roll medley: eight servings, so its own block
			block = block(dish.id(), MedleyBlock::new, properties);
		} else if (feast == Feast.GLEAMING_SALAD) {
			block = block(dish.id(), p -> new FeastBlock(p, feast), properties
				.lightLevel(state -> state.getValue(FeastBlock.SERVINGS) * FeastBlock.SALAD_LIGHT_PER_SERVING));
		} else {
			block = block(dish.id(), p -> new FeastBlock(p, feast), properties);
		}
		Item item = Registry.register(BuiltInRegistries.ITEM, key,
			new FeastItem(block, new Item.Properties().stacksTo(1).setId(key)));
		FEASTS.add(item);
		return item;
	}

	public static void register() {
		COMFORT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, JaymosKitchen.id("comfort"),
			new DelightEffect(MobEffectCategory.BENEFICIAL, 0xB8763A));
		NOURISHMENT = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, JaymosKitchen.id("nourishment"),
			new DelightEffect(MobEffectCategory.BENEFICIAL, 0xD9A440));

		CUTTING_BOARD = block("cutting_board", CuttingBoardBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(1.0F).sound(SoundType.WOOD).noOcclusion());
		CUTTING_BOARD_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("cutting_board"),
			new BlockEntityType<>(CuttingBoardEntity::new, Set.of(CUTTING_BOARD)));
		blockItem("cutting_board", CUTTING_BOARD, new Item.Properties());
		STRAW_BALE = block("straw_bale", HayBlock::new, BlockBehaviour.Properties.ofFullCopy(Blocks.HAY_BLOCK));
		blockItem("straw_bale", STRAW_BALE, new Item.Properties());

		CABBAGES = block("cabbages", CabbageCropBlock::new, crop());
		RICE_CROP = block("rice", RiceBlock::new, crop());
		RICE_PANICLES = block("rice_panicles", RicePaniclesBlock::new, crop());
		WILD_CABBAGES = block("wild_cabbages", WildCropBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision()
			.instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED));

		CABBAGE = food("cabbage", 2, 0.4F, null);
		CABBAGE_LEAF = food("cabbage_leaf", 1, 0.4F, null);
		CABBAGE_SEEDS = blockItem("cabbage_seeds", CABBAGES, new Item.Properties().useItemDescriptionPrefix()
			.compostable(ContextIntProviders.COMPOSTABLE_LOW));
		RICE = blockItem("rice", RICE_CROP, new Item.Properties().useItemDescriptionPrefix().compostable(ContextIntProviders.COMPOSTABLE_LOW));
		RICE_PANICLE = item("rice_panicle", new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_LOW_MEDIUM));
		STRAW = item("straw", new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_LOW));
		blockItem("wild_cabbages", WILD_CABBAGES, new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));

		MINCED_BEEF = food("minced_beef", 2, 0.3F, null);
		BEEF_PATTY = food("beef_patty", 4, 0.8F, null);
		RAW_BACON = food("raw_bacon", 2, 0.3F, null);
		CHICKEN_CUTS = food("chicken_cuts", 1, 0.3F, null);
		COOKED_CHICKEN_CUTS = food("cooked_chicken_cuts", 3, 0.6F, null);
		MUTTON_CHOPS = food("mutton_chops", 1, 0.3F, null);
		COOKED_MUTTON_CHOPS = food("cooked_mutton_chops", 3, 0.8F, null);
		COD_SLICE = food("cod_slice", 1, 0.1F, null);
		COOKED_COD_SLICE = food("cooked_cod_slice", 3, 0.5F, null);
		SALMON_SLICE = food("salmon_slice", 1, 0.1F, null);
		COOKED_SALMON_SLICE = food("cooked_salmon_slice", 3, 0.8F, null);
		// feast servings, eaten from a bowl
		HONEY_GLAZED_HAM = food("honey_glazed_ham", 12, 0.9F, Items.BOWL);
		STUFFED_PUMPKIN = food("stuffed_pumpkin", 11, 0.8F, Items.BOWL);

		IRON_KNIFE = item("iron_knife", new Item.Properties().sword(ToolMaterial.IRON, 0.5F, -1.8F).repairable(Items.IRON_INGOT));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			for (Item item : FOOD_TAB) {
				output.accept(new ItemStack(item));
			}
			for (Item item : FEASTS) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> {
			for (Item item : List.of(CABBAGE_SEEDS, RICE, WILD_CABBAGES.asItem(), STRAW_BALE.asItem())) {
				output.accept(new ItemStack(item));
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(new ItemStack(RICE_PANICLE));
			output.accept(new ItemStack(STRAW));
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(new ItemStack(CUTTING_BOARD));
			output.accept(new ItemStack(IRON_KNIFE));
		});

		// a grown wheat cut with a knife gives straw too
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level.isClientSide() || player.getAbilities().instabuild || !state.is(Blocks.WHEAT)
				|| !(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state) || !player.getMainHandItem().is(KNIVES)) {
				return;
			}
			Block.popResource(level, pos, new ItemStack(STRAW, WHEAT_STRAW));
			player.getMainHandItem().hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		});

		// wild cabbages on the coasts, wild rice in the swamps (data/jaymoskitchen/worldgen)
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_BEACH), GenerationStep.Decoration.VEGETAL_DECORATION, placed("wild_cabbages"));
		BiomeModifications.addFeature(BiomeSelectors.includeByKey(net.minecraft.world.level.biome.Biomes.SWAMP,
			net.minecraft.world.level.biome.Biomes.MANGROVE_SWAMP), GenerationStep.Decoration.VEGETAL_DECORATION, placed("wild_rice"));
	}

	/** Comfort from a hot bowl, Nourishment from a rich meal (from Stomach, as it's eaten; the stack is still whole). */
	public static void onEat(ServerPlayer player, ItemStack stack) {
		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food == null) {
			return;
		}
		Quality quality = Quality.of(stack);
		if (quality == Quality.BURNT || quality == Quality.CHARRED || quality == Quality.UNDERCOOKED) {
			return;
		}
		int times = quality == Quality.PERFECT ? 2 : 1;
		UseRemainder remainder = stack.get(DataComponents.USE_REMAINDER);
		if (remainder != null && remainder.convertInto().item().value() == Items.BOWL) {
			player.addEffect(new MobEffectInstance(COMFORT, COMFORT_TICKS * times));
		}
		if (food.nutrition() >= NOURISHING_FROM) {
			int ticks = (food.nutrition() - NOURISHING_FROM + 1) * NOURISHMENT_PER_POINT * times;
			player.addEffect(new MobEffectInstance(NOURISHMENT, ticks));
		}
	}

	private static ResourceKey<PlacedFeature> placed(String id) {
		return ResourceKey.create(Registries.PLACED_FEATURE, JaymosKitchen.id(id));
	}

	/** As vanilla's wheat: no collision, broken at a touch, ticks to grow, pushed pistons break it. */
	private static BlockBehaviour.Properties crop() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak()
			.sound(SoundType.CROP).pushReaction(PushReaction.POPPED);
	}

	private static Item food(String id, int nutrition, float saturation, Item leaves) {
		FoodProperties food = new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
		Item.Properties properties = new Item.Properties().food(food);
		if (leaves != null) {
			properties.usingConvertsTo(leaves).stacksTo(16);
		}
		Item item = item(id, properties);
		FOOD_TAB.add(item);
		return item;
	}

	private static Item item(String id, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
	}

	private static Item blockItem(String id, Block block, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, properties.setId(key)));
	}

	private static <B extends Block> B block(String name, Function<BlockBehaviour.Properties, B> factory, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}
}
