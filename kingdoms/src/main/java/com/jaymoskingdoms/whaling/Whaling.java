package com.jaymoskingdoms.whaling;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.function.Function;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * (0.7.206) Whaling, stage 1: the {@link GreatWhale great whales} of the deep oceans (our own beast, far bigger than
 * Naturalist's, whose whales {@code Encounters} keeps out of the sea so ours take their place), the roped {@link Harpoon}
 * that ties your boat (or your ship, or you) to one, and the {@link WhaleCarcass carcass} it leaves, floating, to be
 * towed and flensed for blubber, meat, bone and now and then ambergris. Sneak and use shears or an axe to cut your lines.
 * Stage 2: a carcass yields in full only cut in alongside a big ship (see {@link WhaleCarcass}); its blubber is rendered
 * to whale oil in a {@link TryworksBlock tryworks}; and every part of a whale fetches a fine price from the kingdoms'
 * folk ({@link WhaleTrade}). Stage 3 (the kingdoms' whaling crews) comes later.
 */
public final class Whaling {
	/** Great whales come to the deep oceans with this weight, in pods of this many. */
	static final int WEIGHT = 2, POD_MIN = 1, POD_MAX = 3;

	public static EntityType<GreatWhale> GREAT_WHALE;
	public static EntityType<WhaleCarcass> WHALE_CARCASS;
	public static EntityType<Harpoon> HARPOON;
	public static Item HARPOON_ITEM, WHALE_BLUBBER, WHALE_MEAT, COOKED_WHALE_MEAT, WHALE_BONE, AMBERGRIS, GREAT_WHALE_SPAWN_EGG;
	/** Stage 2: the tryworks, and the oil it renders. */
	public static Block TRYWORKS;
	public static Item TRYWORKS_ITEM, WHALE_OIL;
	/** Whale oil's uses: lamps, firebombs and boiling oil pots for the walls. */
	public static Block OIL_LAMP, OIL_POT;
	public static Item OIL_LAMP_ITEM, OIL_POT_ITEM, FIREBOMB_ITEM, POWDER_FIREBOMB_ITEM;
	public static EntityType<Firebomb> FIREBOMB;

	private Whaling() {
	}

	public static void register() {
		GREAT_WHALE = type("great_whale", EntityType.Builder.of(GreatWhale::new, MobCategory.WATER_CREATURE)
			.sized(GreatWhale.WIDTH, GreatWhale.HEIGHT).clientTrackingRange(16));
		WHALE_CARCASS = type("whale_carcass", EntityType.Builder.<WhaleCarcass>of(WhaleCarcass::new, MobCategory.MISC)
			.noLootTable().sized(GreatWhale.WIDTH, WhaleCarcass.HEIGHT).clientTrackingRange(16).updateInterval(2));
		HARPOON = type("harpoon", EntityType.Builder.<Harpoon>of(Harpoon::new, MobCategory.MISC)
			.noLootTable().sized(0.4F, 0.4F).clientTrackingRange(16).updateInterval(1));
		FabricDefaultAttributeRegistry.register(GREAT_WHALE, GreatWhale.createAttributes());
		SpawnPlacements.register(GREAT_WHALE, SpawnPlacementTypes.IN_WATER, Heightmap.Types.OCEAN_FLOOR, GreatWhale::checkSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN), MobCategory.WATER_CREATURE, GREAT_WHALE, WEIGHT, POD_MIN, POD_MAX);

		HARPOON_ITEM = item("harpoon", HarpoonItem::new, new Item.Properties().stacksTo(16));
		WHALE_BLUBBER = item("whale_blubber", Item::new, new Item.Properties());
		WHALE_MEAT = item("whale_meat", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3F).build()));
		COOKED_WHALE_MEAT = item("cooked_whale_meat", Item::new, new Item.Properties()
			.food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).build()));
		WHALE_BONE = item("whale_bone", Item::new, new Item.Properties());
		AMBERGRIS = item("ambergris", Item::new, new Item.Properties().rarity(Rarity.RARE));
		ResourceKey<Item> egg = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("great_whale_spawn_egg"));
		GREAT_WHALE_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, egg, new SpawnEggItem(new Item.Properties().setId(egg).spawnEgg(GREAT_WHALE)));

		ResourceKey<Block> works = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("tryworks"));
		TRYWORKS = Registry.register(BuiltInRegistries.BLOCK, works, new TryworksBlock(BlockBehaviour.Properties.of().setId(works)
			.mapColor(MapColor.COLOR_RED).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE).noOcclusion()
			.lightLevel(state -> state.getValue(TryworksBlock.LIT) ? 13 : 0)));
		ResourceKey<Item> worksItem = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("tryworks"));
		TRYWORKS_ITEM = Registry.register(BuiltInRegistries.ITEM, worksItem, new BlockItem(TRYWORKS, new Item.Properties().setId(worksItem).useBlockDescriptionPrefix()));
		WHALE_OIL = item("whale_oil", Item::new, new Item.Properties().stacksTo(16).craftRemainder(Items.GLASS_BOTTLE));
		WhaleTrade.register();

		OIL_LAMP = block("oil_lamp", OilLampBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).forceSolidOn().strength(3.5F)
			.sound(SoundType.LANTERN).noOcclusion().pushReaction(PushReaction.DESTROY)
			.lightLevel(state -> state.getValue(OilLampBlock.LIT) ? OilLampBlock.LIGHT : 0));
		OIL_LAMP_ITEM = blockItem("oil_lamp", OIL_LAMP);
		OIL_POT = block("oil_pot", OilPotBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F)
			.requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion()
			.lightLevel(state -> state.getValue(OilPotBlock.LIT) ? 10 : 0));
		OIL_POT_ITEM = blockItem("oil_pot", OIL_POT);
		FIREBOMB = type("firebomb", EntityType.Builder.<Firebomb>of(Firebomb::new, MobCategory.MISC)
			.noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10));
		FIREBOMB_ITEM = item("firebomb", FirebombItem::new, new Item.Properties().stacksTo(16));
		POWDER_FIREBOMB_ITEM = item("powder_firebomb", FirebombItem::new, new Item.Properties().stacksTo(16));
		Lamplighters.register();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.insertAfter(Items.SOUL_LANTERN, OIL_LAMP_ITEM);
			output.insertAfter(Items.SMOKER, OIL_POT_ITEM);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(Items.SNOWBALL, FIREBOMB_ITEM);
			output.insertAfter(FIREBOMB_ITEM, POWDER_FIREBOMB_ITEM);
		});

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.insertAfter(Items.SMOKER, TRYWORKS_ITEM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.TRIDENT, HARPOON_ITEM));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.insertAfter(Items.COOKED_SALMON, WHALE_MEAT);
			output.insertAfter(WHALE_MEAT, COOKED_WHALE_MEAT);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.insertAfter(Items.PRISMARINE_CRYSTALS, WHALE_BLUBBER);
			output.insertAfter(WHALE_BLUBBER, WHALE_BONE);
			output.insertAfter(WHALE_BONE, AMBERGRIS);
			output.insertAfter(AMBERGRIS, WHALE_OIL);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> output.insertAfter(Items.DOLPHIN_SPAWN_EGG, GREAT_WHALE_SPAWN_EGG));

		// sneak and use shears or an axe: every line you hold is cut
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack held = player.getItemInHand(hand);
			if (!player.isShiftKeyDown() || !(held.is(Items.SHEARS) || held.is(ItemTags.AXES))) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				return Harpoon.cutLines(server, player) > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			return Harpoon.holdsLines(player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
	}

	private static <T extends Entity> EntityType<T> type(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
	}

	private static Item blockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix()));
	}

	private static Item item(String name, Function<Item.Properties, Item> make, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
	}
}
