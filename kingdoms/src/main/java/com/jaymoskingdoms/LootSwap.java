package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.money.PurseContents;

/**
 * (0.7.498) Coins for emeralds in the loot of the world. Emeralds are a gem now and the kingdoms' money is the coin, so
 * the emeralds in found loot (vanilla's and every other mod's chests, mob drops and the village heroes' gifts) are
 * struck into the coin of the land where they were found, an emerald's worth for each ({@link Money#stacks}: the realm
 * coin of the settlement around, with copper bits for the change; silver where there is none). Only the tables under
 * {@code chests/}, {@code entities/} and {@code gameplay/hero_of_the_village/} are swapped; what is mined (emerald ore
 * and the like, {@code blocks/}) stays a gem, as do the trades' own (their prices are made coins when a trade opens,
 * {@link Currencies#localize}, not here).
 */
public final class LootSwap {
	/** The loot-table folders whose emeralds become coins. */
	static final List<String> FOLDERS = List.of("chests/", "entities/", "gameplay/hero_of_the_village/");
	/** A chest's chance of a money bag (a filled coin purse), and its worth in emeralds: from MIN to MIN + MORE. */
	static final float MONEY_BAG_CHANCE = 0.08F;
	static final int MONEY_BAG_MIN = 2, MONEY_BAG_MORE = 8;

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
		// (0.7.498) every emerald becomes coins of the land where it was found
		Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack drop : drops) {
			if (drop.is(Items.EMERALD)) {
				long milli = Money.worth(drop.getCount());
				out.addAll(origin == null ? Money.stacks(milli) : Money.stacks(context.getLevel(), BlockPos.containing(origin), milli));
			} else {
				out.add(drop);
			}
		}
		// (Andy's pick from Numismatic Overhaul) now and then a chest holds someone's money bag: a coin purse with the
		// coin of the land in it
		if (origin != null && table.unwrapKey().get().identifier().getPath().startsWith("chests/")
			&& context.getRandom().nextFloat() < MONEY_BAG_CHANCE) {
			BlockPos at = BlockPos.containing(origin);
			Currency coin = Currencies.at(context.getLevel(), at);
			long milli = Money.worth(MONEY_BAG_MIN + context.getRandom().nextInt(MONEY_BAG_MORE + 1));
			if (coin.money()) {
				out.add(PurseContents.filled(coin, milli));
			} else {
				out.addAll(Money.stacks(context.getLevel(), at, milli));
			}
		}
		drops.clear();
		drops.addAll(out);
	}
}
