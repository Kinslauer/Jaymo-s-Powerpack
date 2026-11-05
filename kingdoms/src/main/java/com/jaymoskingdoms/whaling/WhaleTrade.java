package com.jaymoskingdoms.whaling;

import com.jaymoskingdoms.Downed;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * (0.7.206) Whaling, stage 2: every part of a whale is prized by the kingdoms' folk (Andy, 2026-10-25: "All the whale
 * parts are extremely valuable to kingdoms and their inhabitants"). A grown villager <b>buys</b> whale goods for emeralds,
 * dearly, by their trade: fishermen and butchers the meat and blubber, clerics the oil for their lamps and ambergris for
 * their incense, librarians and cartographers oil to read and draw by, leatherworkers blubber to dress hides, armourers and
 * weaponsmiths oil against rust, toolsmiths and fletchers bone, masons bone and oil.
 * (0.7.555) Andy: "not everyone should strictly trade for whale products." Only those trades buy whale goods now; the rest
 * want something of their own ({@code OwnWants}), and a whale purchase put on someone outside those trades before is taken off. A sale on a realm's land pays the realm its toll like any
 * other trade ({@code Treasuries.traded}), so a whaling crew's haul fills its kingdom's purse.
 * <p>
 * As with {@code Remedies}, the offers are added when a player right-clicks the villager, just before the trading screen
 * opens; an offer, once made, stays.
 */
final class WhaleTrade {
	/** One purchase: what they buy and how many at a time, the emeralds they give for it, and how often before a restock. */
	record Want(Item item, int count, int emeralds, int uses) {
	}

	/** The trading experience a villager earns per purchase, and how the price shifts with demand (the game's own use 0.05). */
	static final int XP = 5;
	static final float PRICE_MULTIPLIER = 0.05F;

	private WhaleTrade() {
	}

	/** Built when first asked for (the items are only registered when {@link Whaling#register} runs). */
	private static Map<ResourceKey<VillagerProfession>, List<Want>> byTrade;
	/** Every whale good. */
	private static Set<Item> whale;

	static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && !world.isClientSide() && world instanceof ServerLevel && entity instanceof Villager v
				&& v.isAlive() && !v.isBaby() && !Downed.isDowned(v) && !player.isSpectator() && v.getTradingPlayer() == null
				&& !com.jaymoskingdoms.ZFighterFolk.is(v)) {
				stock(v);
			}
			return InteractionResult.PASS;
		});
	}

	private static void wants() {
		if (byTrade != null) {
			return;
		}
		Want meat = new Want(Whaling.WHALE_MEAT, 4, 3, 12);
		Want blubber = new Want(Whaling.WHALE_BLUBBER, 4, 4, 16), oil = new Want(Whaling.WHALE_OIL, 1, 4, 12);
		Want bone = new Want(Whaling.WHALE_BONE, 2, 5, 8);
		byTrade = Map.ofEntries(
			Map.entry(VillagerProfession.FISHERMAN, List.of(meat, blubber, new Want(Whaling.WHALE_BONE, 2, 4, 8))),
			Map.entry(VillagerProfession.BUTCHER, List.of(new Want(Whaling.WHALE_MEAT, 3, 4, 16), blubber)),
			Map.entry(VillagerProfession.CLERIC, List.of(new Want(Whaling.WHALE_OIL, 1, 5, 16), new Want(Whaling.AMBERGRIS, 1, 48, 3))),
			Map.entry(VillagerProfession.LIBRARIAN, List.of(new Want(Whaling.WHALE_OIL, 1, 5, 12))),
			Map.entry(VillagerProfession.CARTOGRAPHER, List.of(new Want(Whaling.WHALE_OIL, 1, 5, 12), bone)),
			Map.entry(VillagerProfession.LEATHERWORKER, List.of(new Want(Whaling.WHALE_BLUBBER, 3, 5, 16), oil)),
			Map.entry(VillagerProfession.ARMORER, List.of(oil)),
			Map.entry(VillagerProfession.WEAPONSMITH, List.of(oil, bone)),
			Map.entry(VillagerProfession.TOOLSMITH, List.of(new Want(Whaling.WHALE_BONE, 2, 6, 10), oil)),
			Map.entry(VillagerProfession.FLETCHER, List.of(new Want(Whaling.WHALE_BONE, 2, 6, 10))),
			Map.entry(VillagerProfession.MASON, List.of(bone, oil)),
			Map.entry(VillagerProfession.FARMER, List.of(blubber)));
		whale = Set.of(Whaling.WHALE_MEAT, Whaling.COOKED_WHALE_MEAT, Whaling.WHALE_BLUBBER, Whaling.WHALE_OIL, Whaling.WHALE_BONE, Whaling.AMBERGRIS);
	}

	/** {@code v}'s whale purchases, their trade's only: each added the first time, and kept after. */
	static void stock(Villager v) {
		wants();
		MerchantOffers offers = v.getOffers();
		Set<Item> theirs = new HashSet<>();
		for (var entry : byTrade.entrySet()) {
			if (v.getVillagerData().profession().is(entry.getKey())) {
				for (Want w : entry.getValue()) {
					theirs.add(w.item());
					offer(offers, w);
				}
			}
		}
		// (0.7.555) everyone used to buy cooked meat, oil and ambergris: off again on those whose trade doesn't
		offers.removeIf(o -> whale.contains(o.getCostA().getItem()) && !theirs.contains(o.getCostA().getItem())
			&& com.jaymoskingdoms.Currencies.isMoney(o.getResult()));
	}

	private static void offer(MerchantOffers offers, Want w) {
		for (MerchantOffer offer : offers) {
			// (0.7.490) paid in any coin: the town's coin they were set in counts too (Currencies.localize)
			if (offer.getCostA().is(w.item()) && com.jaymoskingdoms.Currencies.isMoney(offer.getResult())) {
				// (they already buy it: their trade's dearer price, added first, stands)
				return;
			}
		}
		offers.add(new MerchantOffer(new ItemCost(w.item(), w.count()), new ItemStack(Items.EMERALD, w.emeralds()), w.uses(), XP, PRICE_MULTIPLIER));
	}
}
