package com.jaymoskitchen.forage;

import com.jaymoskitchen.JaymosKitchen;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The forager's things: six small wild mushrooms, truffles and Truffle Soil, Mycelial Soil, the cultivation log, Mycelial
 * Thread and the foraging knife, and the Harvest Hive with its Bee Jar. The mushrooms, soils, log and knife are ported
 * from Spore &amp; Stem (Apache-2.0, by NightBeam; the licence is in licenses/spore-and-stem), with its 3D models and
 * textures, and the Java rewritten here; the hive and jar are our own. The dishes are in cooking/Dish. The glowcap,
 * bouncecap and inkcap belong to Jaymo's Kingdoms and are only looked up by id (see {@link CultivationFungus}).
 */
public final class Forage {
	/** Stack sizes: the empty and the full bee jar. */
	private static final int JARS_PER_STACK = 16;
	/** Light the emberstool gives. */
	private static final int EMBERSTOOL_LIGHT = 7;

	/** Mined fast by the foraging knife. */
	public static final TagKey<Block> MINEABLE_WITH_KNIFE = TagKey.create(Registries.BLOCK, JaymosKitchen.id("mineable/foraging_knife"));

	public static Block MOREL_BLOCK, CHANTERELLE_BLOCK, DEATHCAP_BLOCK, FROSTCAP_BLOCK, EMBERSTOOL_BLOCK, PUFFBALL_BLOCK;
	public static Block MYCELIAL_SOIL, TRUFFLE_SOIL, CULTIVATION_LOG, HARVEST_HIVE;
	public static Item MOREL, CHANTERELLE, DEATHCAP, FROSTCAP, EMBERSTOOL, PUFFBALL, TRUFFLE, MYCELIAL_THREAD, FORAGING_KNIFE;
	public static Item BEE_JAR, JARRED_BEE;
	public static BlockEntityType<HarvestHiveEntity> HIVE_ENTITY;
	/** The bee a jar holds. */
	public static DataComponentType<Occupant> JARRED_BEE_DATA;
	public static SoundEvent LOG_INOCULATE, LOG_HARVEST;

	private static final List<Item> NATURAL_TAB = new ArrayList<>();
	private static final List<Item> FOOD_TAB = new ArrayList<>();

	private Forage() {
	}

	public static void register() {
		LOG_INOCULATE = sound("block.cultivation_log.inoculate");
		LOG_HARVEST = sound("block.cultivation_log.harvest");

		MOREL_BLOCK = mushroom("morel", Block.box(5, 0, 3, 14, 11, 11), MapColor.TERRACOTTA_BROWN, 0, false);
		CHANTERELLE_BLOCK = mushroom("chanterelle", Block.box(2, 0, 3, 13, 7, 13), MapColor.GOLD, 0, true);
		DEATHCAP_BLOCK = mushroom("deathcap", Block.box(4, 0, 4, 12, 10, 12), MapColor.TERRACOTTA_LIGHT_GREEN, 0, false);
		FROSTCAP_BLOCK = mushroom("frostcap", Block.box(1, 0, 5, 11, 9, 12), MapColor.ICE, 0, false);
		EMBERSTOOL_BLOCK = mushroom("emberstool", Block.box(4, 0, 4, 12, 9, 12), MapColor.COLOR_ORANGE, EMBERSTOOL_LIGHT, false);
		PUFFBALL_BLOCK = mushroom("puffball", Block.box(2, 0, 4, 12, 7, 13), MapColor.SNOW, 0, false);

		MYCELIAL_SOIL = block("mycelial_soil", MycelialSoilBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
			.strength(0.6F).sound(SoundType.ROOTED_DIRT).randomTicks());
		TRUFFLE_SOIL = block("truffle_soil", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.6F)
			.sound(SoundType.ROOTED_DIRT));
		CULTIVATION_LOG = block("cultivation_log", CultivationLogBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(1.5F).sound(SoundType.WOOD).noOcclusion().randomTicks().ignitedByLava().lightLevel(Forage::logLight));
		HARVEST_HIVE = block("harvest_hive", HarvestHiveBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(0.6F).sound(SoundType.WOOD).ignitedByLava());
		HIVE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosKitchen.id("harvest_hive"),
			new BlockEntityType<>(HarvestHiveEntity::new, Set.of(HARVEST_HIVE)));

		MOREL = mushroomItem("morel", MOREL_BLOCK, 2, 0.3F);
		CHANTERELLE = mushroomItem("chanterelle", CHANTERELLE_BLOCK, 2, 0.4F);
		// the deathcap is poison
		DEATHCAP = mushroomItem("deathcap", DEATHCAP_BLOCK, 1, 0.1F, new MobEffectInstance(MobEffects.POISON, 200, 1));
		FROSTCAP = mushroomItem("frostcap", FROSTCAP_BLOCK, 1, 0.1F, new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200),
			new MobEffectInstance(MobEffects.SLOWNESS, 100));
		EMBERSTOOL = mushroomItem("emberstool", EMBERSTOOL_BLOCK, 1, 0.1F, new MobEffectInstance(MobEffects.HASTE, 200));
		PUFFBALL = mushroomItem("puffball", PUFFBALL_BLOCK, 2, 0.3F);
		TRUFFLE = item("truffle", Item::new, new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_MEDIUM)
			.food(food(3, 0.6F), Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(
				List.of(new MobEffectInstance(MobEffects.REGENERATION, 100)))).build()));
		MYCELIAL_THREAD = item("mycelial_thread", Item::new, new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_LOW));
		FORAGING_KNIFE = item("foraging_knife", ForagingKnifeItem::new,
			new Item.Properties().tool(ToolMaterial.IRON, MINEABLE_WITH_KNIFE, 0.0F, -2.0F, 0.0F));

		NATURAL_TAB.add(blockItem("mycelial_soil", MYCELIAL_SOIL));
		NATURAL_TAB.add(blockItem("truffle_soil", TRUFFLE_SOIL));
		NATURAL_TAB.add(MOREL);
		NATURAL_TAB.add(CHANTERELLE);
		NATURAL_TAB.add(DEATHCAP);
		NATURAL_TAB.add(FROSTCAP);
		NATURAL_TAB.add(EMBERSTOOL);
		NATURAL_TAB.add(PUFFBALL);
		NATURAL_TAB.add(blockItem("cultivation_log", CULTIVATION_LOG));
		FOOD_TAB.addAll(List.of(MOREL, CHANTERELLE, DEATHCAP, FROSTCAP, EMBERSTOOL, PUFFBALL, TRUFFLE));
		Item hive = blockItem("harvest_hive", HARVEST_HIVE);

		JARRED_BEE_DATA = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("jarred_bee"),
			DataComponentType.<Occupant>builder().persistent(Occupant.CODEC).networkSynchronized(Occupant.STREAM_CODEC).build());
		BEE_JAR = item("bee_jar", BeeJarItem::new, new Item.Properties().stacksTo(JARS_PER_STACK));
		JARRED_BEE = item("bee_in_a_jar", JarredBeeItem::new, new Item.Properties().stacksTo(1));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(output -> NATURAL_TAB.forEach(i -> output.accept(new ItemStack(i))));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			FOOD_TAB.forEach(i -> output.accept(new ItemStack(i)));
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(new ItemStack(MYCELIAL_THREAD)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(new ItemStack(FORAGING_KNIFE));
			output.accept(new ItemStack(BEE_JAR));
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(new ItemStack(hive)));

		// the attachment is class-loaded here
		HiveWorker.register();
		ForageWorld.register();
	}

	/** The cultivation log glows once glowcaps or emberstools show on it. */
	private static int logLight(net.minecraft.world.level.block.state.BlockState state) {
		int stage = state.getValue(CultivationLogBlock.STAGE);
		return switch (state.getValue(CultivationLogBlock.FUNGUS)) {
			case GLOWCAP -> stage >= 2 ? 4 + stage * 2 : 0;
			case EMBERSTOOL -> stage >= 2 ? 2 + stage : 0;
			default -> 0;
		};
	}

	/** A small mushroom block and its flower pot. */
	private static Block mushroom(String id, VoxelShape shape, MapColor color, int light, boolean lateSummerOnly) {
		Block block = block(id, p -> new ForageMushroomBlock(shape, lateSummerOnly, p), BlockBehaviour.Properties.of().mapColor(color)
			.noCollision().instabreak().randomTicks().sound(SoundType.FUNGUS).offsetType(BlockBehaviour.OffsetType.XZ)
			.lightLevel(state -> light).pushReaction(PushReaction.POPPED));
		block("potted_" + id, p -> new FlowerPotBlock(block, p), BlockBehaviour.Properties.of().instabreak().noOcclusion()
			.lightLevel(state -> light).pushReaction(PushReaction.POPPED));
		return block;
	}

	private static Item mushroomItem(String id, Block block, int nutrition, float saturation, MobEffectInstance... effects) {
		Item.Properties properties = new Item.Properties().useBlockDescriptionPrefix().compostable(ContextIntProviders.COMPOSTABLE_MEDIUM);
		if (effects.length == 0) {
			properties.food(food(nutrition, saturation));
		} else {
			properties.food(food(nutrition, saturation), Consumables.defaultFood()
				.onConsume(new ApplyStatusEffectsConsumeEffect(List.of(effects))).build());
		}
		return item(id, p -> new BlockItem(block, p), properties);
	}

	private static FoodProperties food(int nutrition, float saturation) {
		return new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build();
	}

	private static SoundEvent sound(String path) {
		Identifier id = JaymosKitchen.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
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

	private static Item blockItem(String id, Block block) {
		return item(id, p -> new BlockItem(block, p), new Item.Properties().useBlockDescriptionPrefix());
	}
}
