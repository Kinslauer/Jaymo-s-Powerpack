package com.jaymoskingdoms.money;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * (0.7.486) The coinage of villager trades. Every trade a villager or a wandering trader offers is built by
 * {@code VillagerTrade.getOffer} (vanilla's, MCA's and every mod's trade data alike), and that is hooked to run its offer
 * through {@link #convert}, so no trade file names a coin. Offers saved before the coinage are converted once on load
 * (also {@link #convert}, which leaves nothing to convert the second time).
 *
 * <p>Rules (an emerald is one silver coin): an emerald cost of n up to {@value #MOST} is n silver; more than that is gold
 * (ten silver) with the odd silver in the second slot if it is free, else rounded up to a whole gold; an emerald block is
 * nine silver. An emerald in the result is the same silver. Everything else is left as it was.
 */
public final class Coinage {
	/** The most coins one payment slot holds. */
	static final int MOST = 64;

	private Coinage() {
	}

	/** {@code offers} with every emerald price made coins (in place). */
	public static void convert(MerchantOffers offers) {
		for (int i = 0; i < offers.size(); i++) {
			MerchantOffer old = offers.get(i);
			MerchantOffer now = convert(old);
			if (now != old) {
				offers.set(i, now);
			}
		}
	}

	/** {@code offer} with its emerald prices made coins; {@code offer} itself if it has none. */
	public static MerchantOffer convert(MerchantOffer offer) {
		if (Changer.own(offer)) {
			// (0.7.486) the money changer's own rates are already in coins
			return offer;
		}
		long a = silverOf(offer.getItemCostA()), b = offer.getItemCostB().map(Coinage::silverOf).orElse(-1L);
		boolean result = offer.getResult().is(Items.EMERALD);
		if (a < 0L && b < 0L && !result) {
			return offer;
		}
		ItemCost costA = offer.getItemCostA();
		Optional<ItemCost> costB = offer.getItemCostB();
		if (b >= 0L) {
			costB = Optional.of(whole(b));
		}
		if (a >= 0L) {
			int gold = (int)(a / 10L), odd = (int)(a % 10L);
			if (a <= MOST) {
				costA = silver((int)Math.max(1L, a));
			} else if (odd > 0 && costB.isEmpty()) {
				costA = new ItemCost(Coin.GOLD.item(), Math.min(gold, MOST));
				costB = Optional.of(silver(odd));
			} else {
				costA = new ItemCost(Coin.GOLD.item(), (int)Math.min(MOST, gold + (odd > 0 ? 1 : 0)));
			}
		}
		ItemStack give = offer.getResult();
		if (result) {
			int n = give.getCount();
			give = n <= MOST ? Coin.SILVER.stack(n) : Coin.GOLD.stack(Math.min(MOST, (n + 9) / 10));
		}
		MerchantOffer out = new MerchantOffer(costA, costB, give.copy(), offer.getUses(), offer.getMaxUses(), offer.getXp(), offer.getPriceMultiplier(),
			offer.getDemand());
		out.setSpecialPriceDiff(offer.getSpecialPriceDiff());
		return out;
	}

	/** What an emerald cost is worth in silver, or -1 if it isn't emeralds. */
	private static long silverOf(ItemCost cost) {
		if (!cost.components().isEmpty()) {
			return -1L;
		}
		return cost.item().value() == Items.EMERALD ? cost.count() : cost.item().value() == Items.EMERALD_BLOCK ? cost.count() * 9L : -1L;
	}

	private static ItemCost silver(int n) {
		return new ItemCost(Coin.SILVER.item(), n);
	}

	/** A second-slot price of {@code silver} silver, in gold if it is more than a slot of silver (rounded up). */
	private static ItemCost whole(long silver) {
		return silver <= MOST ? silver((int)Math.max(1L, silver)) : new ItemCost(Coin.GOLD.item(), (int)Math.min(MOST, (silver + 9L) / 10L));
	}

	/** What {@code stack} is worth as money in a hand: a coin, a coin stack or a coin purse, in cp (0 for anything else). */
	public static long held(ItemStack stack) {
		if (stack.is(MoneyItems.COIN_PURSE)) {
			return PurseContents.of(stack).value();
		}
		return Coin.valueOf(stack);
	}

	/**
	 * (0.7.486) The trade screen's auto-fill, for a price in coins: when the pack holds the money but not the coin the price
	 * asks for (it is in a purse, or in a larger coin), the missing coins are made from the money the player carries
	 * (change goes back into the purse it came from) and put in the payment slot, which {@code slot} is. {@code price} is the count the offer really asks (demand and
	 * special prices included), not the base cost's.
	 */
	public static ItemStack fill(ServerPlayer player, ItemCost cost, int price, ItemStack slot) {
		Coin coin = null;
		for (Coin c : Coin.values()) {
			if (cost.item().value() == c.item()) {
				coin = c;
			}
		}
		if (coin == null || !cost.components().isEmpty()) {
			return slot;
		}
		int have = slot.isEmpty() ? 0 : slot.getCount();
		int need = Math.min(price, MOST) - have;
		if (need <= 0 || !slot.isEmpty() && !slot.is(coin.item()) || !Money.canPay(player, (long)need * coin.value()) || player.hasInfiniteMaterials()) {
			return slot;
		}
		if (!Money.charge(player, (long)need * coin.value())) {
			return slot;
		}
		return coin.stack(have + need);
	}
}
