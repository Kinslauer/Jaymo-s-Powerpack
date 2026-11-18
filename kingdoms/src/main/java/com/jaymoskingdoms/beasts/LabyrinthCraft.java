package com.jaymoskingdoms.beasts;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.function.Function;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import java.util.Optional;
import java.util.Set;

/**
 * The labyrinth-builders' craft (Lore.md, "The labyrinth-builders' craft"): the forgotten hands that raised the labyrinths
 * worked bronze and fire. Registers what they left:
 * <ul>
 * <li>the <b>geared plates</b> of one of their instruments, found in early dungeon chests ({@link #EARLY_DUNGEONS}), and the {@link LabyrinthCompass} built from them;</li>
 * <li>the <b>rune gates</b> ({@link RuneGateBlock}) and the three <b>rune tablets</b> that open them, set out in a
 * labyrinth when a player first comes near ({@link LabyrinthKeep});</li>
 * <li>the <b>bombardier beetle</b>, its <b>queen</b> and their <b>glands</b> ({@link BombardierBeetle}, {@link BeetleQueen},
 * the {@link GlandFlamer} they fuel);</li>
 * <li>the <b>brazier mimic</b> ({@link BrazierMimic}) and the <b>Afterblow</b> ({@link Afterblow}), the blade that
 * strikes twice.</li>
 * </ul>
 */
public final class LabyrinthCraft {
	/** What an early dungeon chest holds of the plates: its chance, and how many. */
	static final float PLATE_CHANCE = 0.4F;
	static final int PLATE_MIN = 1, PLATE_MAX = 2;
	/** The early dungeon chest tables the plates turn up in. */
	static final Set<String> EARLY_DUNGEONS = Set.of("chests/simple_dungeon", "chests/abandoned_mineshaft", "chests/desert_pyramid",
		"chests/jungle_temple");
	/** The warm lands where the bombardier beetles live. */
	static final TagKey<Biome> HAS_BOMBARDIER = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_bombardier_beetle"));
	/** Their natural spawn weight and group. */
	static final int BOMBARDIER_WEIGHT = 6, BOMBARDIER_MIN = 1, BOMBARDIER_MAX = 3;
	/** The Afterblow's blade: iron's, mended with bronze. */
	static final ToolMaterial AFTERBLOW_BLADE = new ToolMaterial(net.minecraft.tags.BlockTags.INCORRECT_FOR_IRON_TOOL, 500, 6.0F, 2.0F, 14,
		TagKey.create(Registries.ITEM, net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "ingots/bronze")));

	/** The three tablets, in the old tongue: each sets one socket of every gate. */
	public static final String[] TABLET_NAMES = {"anthu", "kesh", "orrim"};

	public static Item GEARED_PLATE, BEETLE_GLAND, LABYRINTH_COMPASS, AFTERBLOW, GLAND_FLAMER, RUNE_GATE_ITEM;
	public static final Item[] TABLETS = new Item[TABLET_NAMES.length];
	public static Block RUNE_GATE;
	public static EntityType<BombardierBeetle> BOMBARDIER;
	public static EntityType<BeetleQueen> QUEEN;
	public static EntityType<BrazierMimic> BRAZIER_MIMIC;

	private LabyrinthCraft() {
	}

	public static void register() {
		GEARED_PLATE = item("geared_plate", Item::new, new Item.Properties());
		BEETLE_GLAND = item("beetle_gland", Item::new, new Item.Properties().stacksTo(16));
		for (int i = 0; i < TABLETS.length; i++) {
			TABLETS[i] = item("rune_tablet_" + TABLET_NAMES[i], Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
		}
		LABYRINTH_COMPASS = item("labyrinth_compass", LabyrinthCompass::new, new Item.Properties().stacksTo(1)
			.component(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.empty(), false)));
		AFTERBLOW = item("afterblow", Afterblow::new, new Item.Properties().sword(AFTERBLOW_BLADE, 3.0F, -2.4F).rarity(Rarity.RARE));
		GLAND_FLAMER = item("gland_flamer", GlandFlamer::new, new Item.Properties().stacksTo(1));

		ResourceKey<Block> gate = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("rune_gate"));
		RUNE_GATE = Registry.register(BuiltInRegistries.BLOCK, gate, new RuneGateBlock(BlockBehaviour.Properties.of().setId(gate)
			.mapColor(MapColor.COLOR_ORANGE).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.DEEPSLATE_BRICKS)
			.pushReaction(PushReaction.IMMOVEABLE).lightLevel(RuneGateBlock::light)));
		ResourceKey<Item> gateItem = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("rune_gate"));
		RUNE_GATE_ITEM = Registry.register(BuiltInRegistries.ITEM, gateItem, new BlockItem(RUNE_GATE, new Item.Properties().setId(gateItem)
			.useBlockDescriptionPrefix()));

		BOMBARDIER = type("bombardier_beetle", EntityType.Builder.of(BombardierBeetle::new, MobCategory.CREATURE).sized(0.9F, 0.7F)
			.clientTrackingRange(8), BombardierBeetle.createAttributes());
		QUEEN = type("beetle_queen", EntityType.Builder.of(BeetleQueen::new, MobCategory.CREATURE).sized(0.9F, 0.7F).clientTrackingRange(10),
			BeetleQueen.createAttributes());
		BRAZIER_MIMIC = type("brazier_mimic", EntityType.Builder.of(BrazierMimic::new, MobCategory.MONSTER).sized(0.9F, 1.4F).fireImmune()
			.clientTrackingRange(8), BrazierMimic.createAttributes());
		SpawnPlacements.register(BOMBARDIER, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_BOMBARDIER), MobCategory.CREATURE, BOMBARDIER, BOMBARDIER_WEIGHT, BOMBARDIER_MIN,
			BOMBARDIER_MAX);
		BeetleQueen.register();
		Afterblow.register();

		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			if (table.value().getParamSet() != LootContextParamSets.CHEST || context.getRandom().nextFloat() >= PLATE_CHANCE
				|| !EARLY_DUNGEONS.contains(table.unwrapKey().map(key -> key.identifier().getPath()).orElse(""))) {
				return;
			}
			drops.add(new ItemStack(GEARED_PLATE, PLATE_MIN + context.getRandom().nextInt(PLATE_MAX - PLATE_MIN + 1)));
		});

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(GEARED_PLATE);
			output.accept(BEETLE_GLAND);
			for (Item tablet : TABLETS) {
				output.accept(tablet);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			output.insertAfter(Items.IRON_SWORD, AFTERBLOW);
			output.accept(GLAND_FLAMER);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.COMPASS, LABYRINTH_COMPASS));
	}

	private static Item item(String name, Function<Item.Properties, Item> make, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(properties.setId(key)));
	}

	private static <T extends net.minecraft.world.entity.LivingEntity> EntityType<T> type(String name, EntityType.Builder<T> builder,
		net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder attributes) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id(name));
		EntityType<T> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
		FabricDefaultAttributeRegistry.register(type, attributes);
		return type;
	}
}
