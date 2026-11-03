package com.jaymoskingdoms;

import java.util.List;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import com.jaymoskingdoms.money.Coin;

/**
 * (0.7.486) Coins for emeralds in the loot of the world. Emeralds are gems now and the kingdoms' money is the coin, so
 * the emeralds in found loot (vanilla's and every other mod's chests, mob drops and the village heroes' gifts) are
 * struck into silver coins on the way out of the loot table, one for one (an emerald was a silver's worth). Only the
 * tables under {@code chests/}, {@code entities/} and {@code gameplay/hero_of_the_village/} are swapped; what is mined
 * (emerald ore and the like, {@code blocks/}) stays a gem, as do the trades' own (the villagers' trade data is
 * converted by {@code Coinage}, not here).
 */
public final class LootSwap {
	/** The loot-table folders whose emeralds become silver coins. */
	static final List<String> FOLDERS = List.of("chests/", "entities/", "gameplay/hero_of_the_village/");

	private LootSwap() {
	}

	public static void register() {
		LootTableEvents.MODIFY_DROPS.register(LootSwap::swap);
	}

	/** Whether the table {@code key} is one whose emeralds are swapped. */
	static boolean swaps(ResourceKey<LootTable> key) {
		String path = key.identifier().getPath();
		for (String folder : FOLDERS) {
			if (path.startsWith(folder)) {
				return true;
			}
		}
		return false;
	}

	private static void swap(Holder<LootTable> table, LootContext context, List<ItemStack> drops) {
		if (table.unwrapKey().filter(LootSwap::swaps).isEmpty()) {
			return;
		}
		for (int i = 0; i < drops.size(); i++) {
			ItemStack drop = drops.get(i);
			if (drop.is(Items.EMERALD)) {
				drops.set(i, Coin.SILVER.stack(drop.getCount()));
			}
		}
	}
}
