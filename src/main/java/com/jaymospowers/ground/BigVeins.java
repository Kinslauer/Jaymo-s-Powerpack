package com.jaymospowers.ground;

import com.jaymospowers.JaymosPowers;
import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Andy's TFC Hardrock picks: "Make veins bigger and rarer. Meaning most villages will not have iron armor or tools and
 * will be more encouraged to trade for it. Diamond and netherite become IMPOSSIBLY rare." Vanilla's iron, copper, gold,
 * diamond and ancient debris blobs come out of every biome ({@link #VANILLA}), and a few great veins go in
 * ({@link BigVeinFeature}): iron about one chunk in 24, copper one in 20, gold one in 48, all of 60 to 110 blocks. Powers'
 * silver is a great vein too (worldgen/placed_feature/ore_silver.json). The numbers are the placed features' rarity
 * filters and the features' sizes (worldgen/feature/big_vein_*.json). New chunks only: land already made keeps what it has.
 * Kingdoms' GroundChanges (the ore a world tier lays under loaded chunks) is cut to match.
 * <p>
 * <b>Diamond and netherite are never mined</b> (Andy, 2026-11-12: "They are leftover from the old world when they were
 * abundant so you'll strictly find them in chests, more so in the nether"). No diamond ore or ancient debris is laid at
 * all; they come only from {@link #OLD_WORLD} chests, a few diamonds at {@link #OVERWORLD_CHANCE}, and from the Nether's,
 * more diamonds at {@link #NETHER_CHANCE} and a netherite scrap at {@link #SCRAP_CHANCE}.
 */
public final class BigVeins {
	/** Vanilla's ore placements taken out of every biome. Coal, redstone, lapis, emerald and Nether gold stay. */
	static final List<String> VANILLA = List.of("ore_iron_upper", "ore_iron_middle", "ore_iron_small", "ore_copper", "ore_copper_large",
		"ore_gold", "ore_gold_lower", "ore_gold_extra", "ore_diamond", "ore_diamond_medium", "ore_diamond_large", "ore_diamond_buried",
		"ore_ancient_debris_large", "ore_debris_small");

	/** The Overworld chests the old world's diamonds turn up in. */
	static final List<ResourceKey<net.minecraft.world.level.storage.loot.LootTable>> OLD_WORLD = List.of(
		net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON, net.minecraft.world.level.storage.loot.BuiltInLootTables.ABANDONED_MINESHAFT,
		net.minecraft.world.level.storage.loot.BuiltInLootTables.STRONGHOLD_CORRIDOR, net.minecraft.world.level.storage.loot.BuiltInLootTables.STRONGHOLD_CROSSING,
		net.minecraft.world.level.storage.loot.BuiltInLootTables.DESERT_PYRAMID, net.minecraft.world.level.storage.loot.BuiltInLootTables.JUNGLE_TEMPLE,
		net.minecraft.world.level.storage.loot.BuiltInLootTables.BURIED_TREASURE, net.minecraft.world.level.storage.loot.BuiltInLootTables.SHIPWRECK_TREASURE,
		net.minecraft.world.level.storage.loot.BuiltInLootTables.WOODLAND_MANSION, net.minecraft.world.level.storage.loot.BuiltInLootTables.ANCIENT_CITY,
		net.minecraft.world.level.storage.loot.BuiltInLootTables.END_CITY_TREASURE);
	/** The Nether's chests, where more of it lies. */
	static final List<ResourceKey<net.minecraft.world.level.storage.loot.LootTable>> NETHER_HOARDS = List.of(
		net.minecraft.world.level.storage.loot.BuiltInLootTables.NETHER_BRIDGE, net.minecraft.world.level.storage.loot.BuiltInLootTables.BASTION_TREASURE,
		net.minecraft.world.level.storage.loot.BuiltInLootTables.BASTION_OTHER, net.minecraft.world.level.storage.loot.BuiltInLootTables.BASTION_BRIDGE,
		net.minecraft.world.level.storage.loot.BuiltInLootTables.BASTION_HOGLIN_STABLE, net.minecraft.world.level.storage.loot.BuiltInLootTables.RUINED_PORTAL);
	/**
	 * Chances of a diamond in an Overworld chest (and of a second, at {@link #MORE} of that), of one in a Nether chest (a
	 * second and a third likewise), and of a netherite scrap there.
	 */
	static final float OVERWORLD_CHANCE = 0.08F, NETHER_CHANCE = 0.25F, SCRAP_CHANCE = 0.1F, MORE = 0.4F;

	private BigVeins() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, JaymosPowers.id("big_vein"), BigVeinFeature.CODEC);
		BiomeModifications.create(JaymosPowers.id("smaller_vanilla_ores")).add(ModificationPhase.REMOVALS, BiomeSelectors.all(), context -> {
			for (String id : VANILLA) {
				context.getGenerationSettings().removeFeature(GenerationStep.Decoration.UNDERGROUND_ORES,
					ResourceKey.create(Registries.PLACED_FEATURE, Identifier.withDefaultNamespace(id)));
			}
		});
		for (String id : List.of("big_vein_iron", "big_vein_copper", "big_vein_gold")) {
			BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES, placed(id));
		}
		net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY.register((table, builder, source, registries) -> {
			if (!source.isBuiltin()) {
				return;
			}
			if (OLD_WORLD.contains(table)) {
				builder.withPool(hoard(net.minecraft.world.item.Items.DIAMOND, OVERWORLD_CHANCE));
				builder.withPool(hoard(net.minecraft.world.item.Items.DIAMOND, OVERWORLD_CHANCE * MORE));
			} else if (NETHER_HOARDS.contains(table)) {
				builder.withPool(hoard(net.minecraft.world.item.Items.DIAMOND, NETHER_CHANCE));
				builder.withPool(hoard(net.minecraft.world.item.Items.DIAMOND, NETHER_CHANCE * MORE));
				builder.withPool(hoard(net.minecraft.world.item.Items.DIAMOND, NETHER_CHANCE * MORE * MORE));
				builder.withPool(hoard(net.minecraft.world.item.Items.NETHERITE_SCRAP, SCRAP_CHANCE));
			}
		});
	}

	/** A pool that now and then gives one {@code item}. */
	private static net.minecraft.world.level.storage.loot.LootPool.Builder hoard(net.minecraft.world.item.Item item, float chance) {
		return net.minecraft.world.level.storage.loot.LootPool.lootPool()
			.when(net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition.randomChance(chance))
			.add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(item));
	}

	private static ResourceKey<PlacedFeature> placed(String id) {
		return ResourceKey.create(Registries.PLACED_FEATURE, JaymosPowers.id(id));
	}
}
