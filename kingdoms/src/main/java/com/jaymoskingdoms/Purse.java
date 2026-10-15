package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

/**
 * (0.7.51) What a player can pay with: the {@code item}s loose in their inventory and those carried in a bundle there.
 * Every price the kingdoms ask (bargains, tolls, writs, skills, stables, rickshaws, a retinue, a lost coach fare) is
 * counted and taken here, so emeralds in a bundle spend like any others. Loose ones go first.
 */
public final class Purse {
	private Purse() {
	}

	/** How many {@code item}s {@code player} carries, bundles included. */
	public static int count(ServerPlayer player, Item item) {
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

	/** Takes up to {@code n} {@code item}s from {@code player}: loose ones first, then from their bundles. */
	public static void take(ServerPlayer player, Item item, int n) {
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
