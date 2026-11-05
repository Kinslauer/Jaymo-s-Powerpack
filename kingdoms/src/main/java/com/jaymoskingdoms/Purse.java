package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import com.jaymoskingdoms.money.Money;

/**
 * (0.7.51) What a player can pay with. (0.7.498) Now a thin wrapper over {@link Money}, kept so the old call sites compile
 * while they move over: emeralds are gems and no longer money, so {@code count(player, EMERALD)} is the money the player
 * carries (coins, purses, bundles and the worn purse) in emeralds' worth, and {@code take(player, EMERALD, n)} takes
 * {@code n} emeralds' worth in coin. Any other item is counted loose and in bundles as before. New code calls {@link Money} directly.
 */
public final class Purse {
	private static boolean warned;

	private Purse() {
	}

	private static void deprecated() {
		if (!warned) {
			warned = true;
			JaymosKingdoms.LOGGER.warn("Purse.count/take(EMERALD) is deprecated: use Money.canPay/charge (a price in emeralds is emeralds' worth of coin)",
				new Throwable("first caller"));
		}
	}

	/** How many {@code item}s {@code player} carries, bundles included; for emeralds, how many emeralds' worth of money. */
	public static int count(ServerPlayer player, Item item) {
		if (item == Items.EMERALD) {
			deprecated();
			return (int)Math.min(Integer.MAX_VALUE, Money.count(player) / Money.UNIT);
		}
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(item)) {
				n += stack.getCount();
			}
			BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
			if (bundle != null) {
				n += bundle.itemCopies().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
			}
		}
		return n;
	}

	/** Takes up to {@code n} {@code item}s from {@code player}: loose ones first, then from their bundles; for emeralds, {@code n} emeralds' worth in coin. */
	public static void take(ServerPlayer player, Item item, int n) {
		if (item == Items.EMERALD) {
			deprecated();
			Money.chargeUpTo(player, Money.worth(n));
			return;
		}
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(item)) {
				int k = Math.min(n, stack.getCount());
				stack.shrink(k);
				n -= k;
			}
		}
		for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
			ItemStack stack = inv.getItem(i);
			BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
			if (bundle == null || bundle.itemCopies().noneMatch(s -> s.is(item))) {
				continue;
			}
			List<ItemStack> kept = new ArrayList<>();
			for (ItemStack inside : bundle.itemCopies().toList()) {
				if (n > 0 && inside.is(item)) {
					int k = Math.min(n, inside.getCount());
					inside.shrink(k);
					n -= k;
				}
				if (!inside.isEmpty()) {
					kept.add(inside);
				}
			}
			stack.set(DataComponents.BUNDLE_CONTENTS, bundle.copyWithContents(kept.stream()));
		}
		inv.setChanged();
	}
}
