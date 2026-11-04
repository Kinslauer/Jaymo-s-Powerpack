package com.jaymoskingdoms.money;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import com.jaymoskingdoms.Currency;

/**
 * (0.7.498) Coins in the hand and in the trade screen. A trade's prices are made coins when it opens
 * ({@link com.jaymoskingdoms.Currencies#localize}), so nothing here converts offers; who still deals in emeralds is
 * {@link com.jaymoskingdoms.Currencies#keeper}.
 */
public final class Coinage {
	/** The most coins one payment slot holds. */
	static final int MOST = 64;

	private Coinage() {
	}

	/** What {@code stack} is worth as money in a hand: a coin stack or a coin purse, in milli at the standard rate (0 for anything else, emeralds too). */
	public static long held(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0L;
		}
		if (stack.is(MoneyItems.COIN_PURSE)) {
			return PurseContents.of(stack).value();
		}
		Currency c = Currency.byItem(stack.getItem());
		return c != null && c.money() ? (long)stack.getCount() * c.base : 0L;
	}

	/** (0.7.498) Whether {@code stack} is money held out in a hand: a coin, a stack of coins or a coin purse (empty or not). */
	public static boolean isMoney(ItemStack stack) {
		return stack.is(MoneyItems.COIN_PURSE) || held(stack) > 0L;
	}

	/**
	 * (0.7.498) The trade screen's auto-fill, for a price in coins: when the pack holds the coins the price asks for but not
	 * loose (they are in a purse or a bundle), they are taken from there and put in the payment slot, which {@code slot} is.
	 * Only the exact coin the price asks for: a slot is strict, and another realm's coin is the money-changer's work
	 * ({@link Money#charge} takes those for everything else). {@code price} is the count the offer really asks (demand and
	 * special prices included), not the base cost's.
	 */
	public static ItemStack fill(ServerPlayer player, ItemCost cost, int price, ItemStack slot) {
		Currency coin = Currency.byItem(cost.item().value());
		if (coin == null || !coin.money() || !cost.components().isEmpty()) {
			return slot;
		}
		int have = slot.isEmpty() ? 0 : slot.getCount();
		int need = Math.min(price, MOST) - have;
		if (need <= 0 || !slot.isEmpty() && !slot.is(coin.item()) || player.hasInfiniteMaterials()) {
			return slot;
		}
		if (!Money.chargeCoin(player, coin, need)) {
			return slot;
		}
		return new ItemStack(coin.item(), have + need);
	}
}
