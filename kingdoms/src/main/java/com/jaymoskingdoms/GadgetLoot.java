package com.jaymoskingdoms;

import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Where the magic items lie: the gadgets in old ruins and dungeons, the wands and staffs deeper (a staff in the strongholds
 * and the trial vaults), the Deck of Fates only in the rarest chests, and an old relic (possibly cursed, possibly with a mind
 * of its own) in the chests that guard the most.
 */
public final class GadgetLoot {
	private GadgetLoot() {
	}

	private static final Map<ResourceKey<LootTable>, Float> GADGETS = Map.of(BuiltInLootTables.SIMPLE_DUNGEON, 0.10F, BuiltInLootTables.ABANDONED_MINESHAFT, 0.06F,
		BuiltInLootTables.DESERT_PYRAMID, 0.12F, BuiltInLootTables.JUNGLE_TEMPLE, 0.12F, BuiltInLootTables.STRONGHOLD_CORRIDOR, 0.08F,
		BuiltInLootTables.STRONGHOLD_LIBRARY, 0.10F, BuiltInLootTables.WOODLAND_MANSION, 0.14F, BuiltInLootTables.PILLAGER_OUTPOST, 0.08F,
		BuiltInLootTables.IGLOO_CHEST, 0.08F, BuiltInLootTables.RUINED_PORTAL, 0.05F);
	private static final Map<ResourceKey<LootTable>, Float> WANDS = Map.of(BuiltInLootTables.STRONGHOLD_LIBRARY, 0.14F, BuiltInLootTables.WOODLAND_MANSION, 0.12F,
		BuiltInLootTables.END_CITY_TREASURE, 0.20F, BuiltInLootTables.TRIAL_CHAMBERS_REWARD_RARE, 0.10F, BuiltInLootTables.ANCIENT_CITY, 0.10F,
		BuiltInLootTables.DESERT_PYRAMID, 0.05F, BuiltInLootTables.SIMPLE_DUNGEON, 0.04F);
	private static final Map<ResourceKey<LootTable>, Float> RELICS = Map.of(BuiltInLootTables.END_CITY_TREASURE, 0.25F, BuiltInLootTables.ANCIENT_CITY, 0.18F,
		BuiltInLootTables.WOODLAND_MANSION, 0.18F, BuiltInLootTables.BASTION_TREASURE, 0.25F, BuiltInLootTables.TRIAL_CHAMBERS_REWARD_RARE, 0.15F,
		BuiltInLootTables.STRONGHOLD_CORRIDOR, 0.06F, BuiltInLootTables.DESERT_PYRAMID, 0.08F, BuiltInLootTables.BURIED_TREASURE, 0.10F);
	private static final Map<ResourceKey<LootTable>, Float> DECKS = Map.of(BuiltInLootTables.END_CITY_TREASURE, 0.04F, BuiltInLootTables.ANCIENT_CITY, 0.03F,
		BuiltInLootTables.TRIAL_CHAMBERS_REWARD_RARE, 0.03F, BuiltInLootTables.WOODLAND_MANSION, 0.03F);

	static void register() {
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			ResourceKey<LootTable> key = table.unwrapKey().orElse(null);
			if (key == null) {
				return;
			}
			RandomSource random = context.getRandom();
			Float gadget = GADGETS.get(key), wand = WANDS.get(key), relic = RELICS.get(key), deck = DECKS.get(key);
			if (gadget != null && random.nextFloat() < gadget) {
				List<Item> all = List.of(ImmovableRod.ITEM, ClimbingRope.ITEM, EndlessJug.ITEM, FloatingLight.ITEM, StoneHound.ITEM, BreakingHorn.ITEM, SmokeBottle.ITEM);
				drops.add(new ItemStack(all.get(random.nextInt(all.size()))));
			}
			if (wand != null && random.nextFloat() < wand) {
				List<Item> all = ChargedWand.all();
				drops.add(new ItemStack(all.get(random.nextInt(all.size()))));
			}
			if (relic != null && random.nextFloat() < relic) {
				ItemStack found = Relics.found(context.getLevel(), random);
				if (!found.isEmpty()) {
					drops.add(found);
				}
			}
			if (deck != null && random.nextFloat() < deck) {
				drops.add(new ItemStack(DeckOfFates.ITEM));
			}
		});
	}
}
