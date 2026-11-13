package com.jaymospowers.baubles.artifacts;

import static com.jaymospowers.baubles.artifacts.ArtifactBaubles.*;
import static net.minecraft.world.level.storage.loot.BuiltInLootTables.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * (Artifacts) Where the Artifacts baubles turn up. Like Artifacts they are never crafted; unlike it there are no mimics or
 * campsites of its own. Each one has homes that suit it (reed breathers and frogskin flippers in shipwrecks, the obsidian skull in the
 * Nether, the chorus totem in End cities, shoes and hats in villages and camps, the best ones deep down in ancient cities,
 * mansions and trial vaults). A chest (or a catch, a brushed block, a cat's gift) from one of those homes may hold one
 * bauble picked from those that live there. Chests from other mods' structures (castles, Lost Cities, Repurposed
 * Structures, the Aether's ruins) may hold any of them, less often.
 */
public final class ArtifactLoot {
	/** The chance any chest from another mod's structure holds an Artifacts bauble. */
	static final float MODDED_CHEST = 0.025F;

	private record Home(float chance, List<Item> pool) {
	}

	private static final Map<ResourceKey<LootTable>, Home> HOMES = new HashMap<>();

	// the groups of places, each with how likely one of its chests holds a bauble
	private static final Object[] UNDERGROUND = {SIMPLE_DUNGEON, 0.12F, ABANDONED_MINESHAFT, 0.05F, STRONGHOLD_CORRIDOR, 0.08F, STRONGHOLD_CROSSING, 0.08F,
		TRIAL_CHAMBERS_REWARD_COMMON, 0.04F};
	private static final Object[] TOWN = {VILLAGE_PLAINS_HOUSE, 0.03F, VILLAGE_SAVANNA_HOUSE, 0.03F, VILLAGE_TEMPLE, 0.06F, VILLAGE_WEAPONSMITH, 0.04F,
		VILLAGE_TANNERY, 0.06F, PILLAGER_OUTPOST, 0.08F, ABANDONED_CAMP_COMMON_CHEST, 0.08F, ABANDONED_CAMP_SECRET_CHEST, 0.25F, ABANDONED_CAMP_BARREL, 0.03F};
	private static final Object[] WATER = {SHIPWRECK_TREASURE, 0.12F, SHIPWRECK_SUPPLY, 0.04F, BURIED_TREASURE, 0.15F, UNDERWATER_RUIN_BIG, 0.08F,
		UNDERWATER_RUIN_SMALL, 0.04F, OCEAN_RUIN_COLD_ARCHAEOLOGY, 0.04F, OCEAN_RUIN_WARM_ARCHAEOLOGY, 0.04F, VILLAGE_FISHER, 0.06F, FISHING_TREASURE, 0.05F};
	private static final Object[] NETHER = {NETHER_BRIDGE, 0.10F, BASTION_TREASURE, 0.2F, BASTION_OTHER, 0.08F, BASTION_BRIDGE, 0.08F,
		BASTION_HOGLIN_STABLE, 0.08F, RUINED_PORTAL, 0.05F};
	private static final Object[] COLD = {IGLOO_CHEST, 0.25F, VILLAGE_SNOWY_HOUSE, 0.04F, VILLAGE_TAIGA_HOUSE, 0.03F, ANCIENT_CITY_ICE_BOX, 0.1F};
	private static final Object[] DESERT = {DESERT_PYRAMID, 0.12F, VILLAGE_DESERT_HOUSE, 0.03F, DESERT_PYRAMID_ARCHAEOLOGY, 0.04F,
		DESERT_WELL_ARCHAEOLOGY, 0.06F};
	private static final Object[] JUNGLE = {JUNGLE_TEMPLE, 0.2F, TRAIL_RUINS_ARCHAEOLOGY_RARE, 0.05F};
	private static final Object[] DEEP = {ANCIENT_CITY, 0.12F, WOODLAND_MANSION, 0.15F, STRONGHOLD_LIBRARY, 0.1F, TRIAL_CHAMBERS_REWARD_RARE, 0.1F,
		TRIAL_CHAMBERS_REWARD_UNIQUE, 0.15F, TRIAL_CHAMBERS_REWARD_OMINOUS_RARE, 0.15F, TRIAL_CHAMBERS_REWARD_OMINOUS_UNIQUE, 0.2F};
	private static final Object[] END = {END_CITY_TREASURE, 0.15F};
	/** Only the kitty slippers: a cat may bring a pair back one morning. */
	private static final Object[] CAT = {CAT_MORNING_GIFT, 0.02F};

	private ArtifactLoot() {
	}

	static void init() {
		home(ALEWIFE_CAP, TOWN, UNDERGROUND);
		home(JESTER_CAP, TOWN, DEEP);
		home(REED_BREATHER, WATER);
		home(OWL_MASK, UNDERGROUND, DEEP);
		home(VILLAGER_HAT, TOWN);
		home(SOOTHSAYER_HOOD, UNDERGROUND, DEEP);
		home(RIDERS_HAT, DESERT, TOWN);
		home(FISHERS_HAT, WATER);

		home(LUCKY_SCARF, UNDERGROUND, DEEP);
		home(SCARF_OF_INVISIBILITY, DEEP);
		home(CROSS_NECKLACE, TOWN, DESERT, UNDERGROUND);
		home(PANIC_NECKLACE, UNDERGROUND, JUNGLE);
		home(SHOCK_PENDANT, DEEP, TOWN);
		home(FLAME_PENDANT, NETHER);
		home(THORN_PENDANT, JUNGLE, UNDERGROUND);

		home(CHARM_OF_SINKING, WATER);
		home(CHARM_OF_SHRINKING, END, DEEP);
		home(CHORUS_TOTEM, END);

		home(CLOUD_IN_A_BOTTLE, DEEP, END, COLD);
		home(OBSIDIAN_SKULL, NETHER);
		home(ANTIDOTE_VESSEL, JUNGLE, DESERT, UNDERGROUND);
		home(LODESTONE_CHARM, UNDERGROUND, DEEP);
		home(CRYSTAL_HEART, DEEP);
		home(WIND_BLADDER, WATER, TOWN);
		home(ENDER_POUCH, END);
		home(JESTERS_CUSHION, TOWN, UNDERGROUND);

		home(DIGGING_CLAWS, UNDERGROUND);
		home(FERAL_CLAWS, JUNGLE, COLD);
		home(IRON_KNUCKLES, UNDERGROUND, DEEP);
		home(FIRE_GAUNTLET, NETHER);
		home(RAM_BRACER, UNDERGROUND, JUNGLE);
		home(VAMPIRIC_GLOVE, DEEP, NETHER);
		home(GOLDEN_HOOK, WATER, NETHER);
		home(SMELTERS_BRACER, UNDERGROUND, NETHER);
		home(WITHERED_BRACELET, NETHER);
		home(GLUTTONS_RING, TOWN);

		home(WATERSTRIDER_BOOTS, WATER);
		home(HARE_BOOTS, TOWN, UNDERGROUND);
		home(KITTY_SLIPPERS, TOWN, UNDERGROUND, CAT);
		home(COURIERS_BOOTS, TOWN, UNDERGROUND);
		home(SNOWSHOES, COLD);
		home(STEADFAST_SPIKES, COLD, UNDERGROUND);
		home(FROGSKIN_FLIPPERS, WATER);
		home(ROOTED_BOOTS, JUNGLE, TOWN);
		home(STRIDER_SHOES, NETHER);

		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			ResourceKey<LootTable> key = table.unwrapKey().orElse(null);
			if (key == null) {
				return;
			}
			Home home = HOMES.get(key);
			if (home != null) {
				if (context.getRandom().nextFloat() < home.chance()) {
					drops.add(com.jaymospowers.baubles.Wondrous.dress(new ItemStack(home.pool().get(context.getRandom().nextInt(home.pool().size()))), context.getRandom()));
				}
			} else if (!"minecraft".equals(key.identifier().getNamespace()) && key.identifier().getPath().startsWith("chests/")
				&& context.getRandom().nextFloat() < MODDED_CHEST) {
				drops.add(com.jaymospowers.baubles.Wondrous.dress(new ItemStack(ALL.get(context.getRandom().nextInt(ALL.size()))), context.getRandom()));
			}
		});
	}

	/** Lets {@code item} turn up in every place of these groups. */
	@SuppressWarnings("unchecked")
	private static void home(Item item, Object[]... groups) {
		for (Object[] group : groups) {
			for (int i = 0; i < group.length; i += 2) {
				ResourceKey<LootTable> key = (ResourceKey<LootTable>) group[i];
				float chance = (Float) group[i + 1];
				HOMES.computeIfAbsent(key, k -> new Home(chance, new ArrayList<>())).pool().add(item);
			}
		}
	}
}
