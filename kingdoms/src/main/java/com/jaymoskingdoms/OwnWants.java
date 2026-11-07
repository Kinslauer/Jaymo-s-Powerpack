package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * (0.7.555) Andy: "not everyone should strictly trade for whale products. they should all have some separate thing THEY want
 * specifically." Every grown villager has one thing of their own they'd pay well for, picked once from who they are (their
 * UUID, so it never changes). (0.7.555, Andy: "every villager should want something different, it could relate to their
 * temperament or personality") Mostly it comes from their MCA personality ({@link #byNature}: the greedy want gold, the gloomy
 * candles, the flirty flowers, the athletic a hearty meal...), sometimes from their trade, now and then a small pleasure
 * anyone might want; several things per personality, so two of a kind needn't want the same. It goes on their trades as a purchase when a player right-clicks them, like the whale goods
 * ({@code whaling.WhaleTrade}), which now go only to the trades that use them. Not on Powers' Z Fighter characters.
 */
final class OwnWants {
	/** One want: what, how many at a time, the emeralds paid, and how often before a restock. */
	record Want(Item item, int count, int emeralds, int uses) {
	}

	/** Trading experience per purchase, and how the price shifts with demand (the game's own use 0.05). */
	static final int XP = 4;
	static final float PRICE_MULTIPLIER = 0.05F;
	/** The chances (out of 1) the want comes from their personality's list, else their trade's; the rest, everyone's. */
	static final float NATURE_SHARE = 0.6F, TRADE_SHARE = 0.6F;

	private static Map<ResourceKey<VillagerProfession>, List<Want>> byTrade;
	private static List<Want> anyone;
	private static Map<String, List<Want>> byNature;

	private OwnWants() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && !world.isClientSide() && world instanceof ServerLevel && entity instanceof Villager v
				&& v.isAlive() && !v.isBaby() && !Downed.isDowned(v) && !player.isSpectator() && v.getTradingPlayer() == null
				&& !ZFighterFolk.is(v)) {
				stock(v);
			}
			return InteractionResult.PASS;
		});
	}

	private static void wants() {
		if (byTrade != null) {
			return;
		}
		byTrade = Map.ofEntries(
			Map.entry(VillagerProfession.FARMER, List.of(new Want(Items.BONE_MEAL, 12, 3, 12), new Want(Items.BEETROOT_SEEDS, 16, 2, 12),
				new Want(Items.SUGAR_CANE, 12, 3, 12))),
			Map.entry(VillagerProfession.FISHERMAN, List.of(new Want(Items.INK_SAC, 4, 3, 12), new Want(Items.NAUTILUS_SHELL, 1, 10, 3),
				new Want(Items.PRISMARINE_SHARD, 6, 4, 8))),
			Map.entry(VillagerProfession.BUTCHER, List.of(new Want(Items.SALMON, 6, 3, 12), new Want(Items.RABBIT, 4, 3, 12),
				new Want(Items.SUGAR, 10, 2, 12))),
			Map.entry(VillagerProfession.CLERIC, List.of(new Want(Items.GLOWSTONE_DUST, 6, 4, 10), new Want(Items.NETHER_WART, 8, 4, 10),
				new Want(Items.SPIDER_EYE, 4, 3, 12))),
			Map.entry(VillagerProfession.LIBRARIAN, List.of(new Want(Items.FEATHER, 12, 2, 12), new Want(Items.INK_SAC, 6, 3, 12),
				new Want(Items.GLOW_INK_SAC, 2, 6, 6))),
			Map.entry(VillagerProfession.CARTOGRAPHER, List.of(new Want(Items.COMPASS, 1, 6, 4), new Want(Items.GLASS_PANE, 8, 3, 10),
				new Want(Items.SPYGLASS, 1, 12, 2))),
			Map.entry(VillagerProfession.LEATHERWORKER, List.of(new Want(Items.RABBIT_HIDE, 6, 4, 10), new Want(Items.STRING, 12, 2, 12),
				new Want(Items.TURTLE_SCUTE, 1, 10, 3))),
			Map.entry(VillagerProfession.ARMORER, List.of(new Want(Items.LAVA_BUCKET, 1, 6, 4), new Want(Items.IRON_NUGGET, 18, 3, 10),
				new Want(Items.GOLD_INGOT, 2, 5, 8))),
			Map.entry(VillagerProfession.WEAPONSMITH, List.of(new Want(Items.FLINT, 10, 2, 12), new Want(Items.QUARTZ, 8, 4, 10),
				new Want(Items.AMETHYST_SHARD, 4, 5, 8))),
			Map.entry(VillagerProfession.TOOLSMITH, List.of(new Want(Items.COAL, 16, 2, 16), new Want(Items.COPPER_INGOT, 6, 3, 12),
				new Want(Items.OBSIDIAN, 2, 5, 6))),
			Map.entry(VillagerProfession.FLETCHER, List.of(new Want(Items.FEATHER, 16, 3, 12), new Want(Items.FLINT, 12, 2, 12),
				new Want(Items.SLIME_BALL, 2, 4, 8))),
			Map.entry(VillagerProfession.MASON, List.of(new Want(Items.CALCITE, 8, 3, 10), new Want(Items.TUFF, 12, 2, 12),
				new Want(Items.AMETHYST_SHARD, 4, 5, 8))),
			Map.entry(VillagerProfession.SHEPHERD, List.of(new Want(Items.WHEAT, 16, 2, 16), new Want(Items.SHEARS, 1, 4, 4),
				new Want(Items.COCOA_BEANS, 8, 3, 10))));
		byNature = Map.ofEntries(
			Map.entry("greedy", List.of(new Want(Items.GOLD_NUGGET, 18, 3, 10), new Want(Items.RAW_GOLD, 2, 5, 8), new Want(Items.DIAMOND, 1, 14, 2))),
			Map.entry("gloomy", List.of(new Want(Items.CANDLE, 4, 3, 10), new Want(Items.WITHER_ROSE, 1, 10, 2), new Want(Items.MUSIC_DISC_11, 1, 14, 1))),
			Map.entry("crabby", List.of(new Want(Items.COOKED_BEEF, 4, 3, 10), new Want(Items.PUFFERFISH, 1, 6, 4), new Want(Items.SPIDER_EYE, 3, 3, 8))),
			Map.entry("grumpy", List.of(new Want(Items.COOKED_MUTTON, 4, 3, 10), new Want(Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), 4, 3, 8), new Want(Items.PUFFERFISH, 1, 6, 4))),
			Map.entry("stubborn", List.of(new Want(Items.IRON_INGOT, 3, 4, 8), new Want(Items.OBSIDIAN, 2, 5, 6), new Want(Items.FLINT, 10, 2, 10))),
			Map.entry("friendly", List.of(new Want(Items.COOKIE, 8, 3, 10), new Want(Items.CAKE, 1, 6, 3), new Want(Items.POPPY, 6, 2, 10))),
			Map.entry("upbeat", List.of(new Want(Items.SUNFLOWER, 4, 3, 8), new Want(Items.FIREWORK_ROCKET, 3, 4, 8), new Want(Items.GLOW_BERRIES, 8, 3, 10))),
			Map.entry("peaceful", List.of(new Want(Items.LILY_OF_THE_VALLEY, 4, 3, 8), new Want(Items.HONEY_BOTTLE, 2, 4, 8), new Want(Items.BAMBOO, 16, 2, 12))),
			Map.entry("relaxed", List.of(new Want(Items.HONEY_BOTTLE, 2, 4, 8), new Want(Items.MUSIC_DISC_CAT, 1, 14, 1), new Want(Items.MELON_SLICE, 12, 2, 12))),
			Map.entry("lazy", List.of(new Want(Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), 6, 3, 10), new Want(Items.PUMPKIN_PIE, 2, 4, 8), new Want(Items.BREAD, 6, 2, 12))),
			Map.entry("anxious", List.of(new Want(Items.TORCH, 16, 2, 12), new Want(Items.RABBIT_FOOT, 1, 8, 3), new Want(Items.IRON_DOOR, 1, 5, 3))),
			Map.entry("sensitive", List.of(new Want(Items.ALLIUM, 4, 3, 8), new Want(Items.PAINTING, 1, 4, 4), new Want(Items.PINK_PETALS, 8, 3, 8))),
			Map.entry("shy", List.of(new Want(Items.BOOK, 2, 4, 8), new Want(Items.WRITABLE_BOOK, 1, 5, 4), new Want(Items.LANTERN, 1, 4, 6))),
			Map.entry("introverted", List.of(new Want(Items.BOOK, 2, 4, 8), new Want(Items.FLOWER_POT, 2, 2, 8), new Want(Items.CANDLE, 4, 3, 10))),
			Map.entry("extroverted", List.of(new Want(Items.FIREWORK_ROCKET, 4, 4, 8), new Want(Items.NOTE_BLOCK, 1, 4, 4), new Want(Items.CAKE, 1, 6, 3))),
			Map.entry("flirty", List.of(new Want(Items.ROSE_BUSH, 2, 4, 8), new Want(Items.PINK_TULIP, 4, 3, 8), new Want(Items.AMETHYST_SHARD, 3, 5, 8))),
			Map.entry("witty", List.of(new Want(Items.BOOK, 2, 4, 8), new Want(Items.GOAT_HORN, 1, 10, 2), new Want(Items.GLOW_INK_SAC, 2, 6, 6))),
			Map.entry("playful", List.of(new Want(Items.SLIME_BALL, 3, 4, 8), new Want(Items.SNOWBALL, 16, 2, 12), new Want(Items.SWEET_BERRIES, 12, 2, 12))),
			Map.entry("odd", List.of(new Want(Items.FERMENTED_SPIDER_EYE, 1, 5, 4), new Want(Items.PHANTOM_MEMBRANE, 1, 8, 3), new Want(Items.ECHO_SHARD, 1, 16, 1))),
			Map.entry("athletic", List.of(new Want(Items.COOKED_PORKCHOP, 4, 3, 10), new Want(Items.GOLDEN_CARROT, 3, 5, 8), new Want(Items.LEATHER, 4, 3, 10))),
			Map.entry("confident", List.of(new Want(Items.GOLD_INGOT, 2, 5, 8), new Want(Items.SHIELD, 1, 5, 3), new Want(Items.SPYGLASS, 1, 12, 2))));
		anyone = List.of(
			new Want(Items.HONEY_BOTTLE, 2, 4, 8), new Want(Items.SWEET_BERRIES, 12, 2, 12), new Want(Items.RABBIT_FOOT, 1, 8, 3),
			new Want(Items.CAKE, 1, 6, 3), new Want(Items.COOKIE, 8, 3, 10), new Want(Items.PUMPKIN_PIE, 2, 4, 8),
			new Want(Items.GLOW_BERRIES, 8, 3, 10), new Want(Items.AMETHYST_SHARD, 3, 5, 8), new Want(Items.MUSIC_DISC_CAT, 1, 14, 1),
			new Want(Items.PAINTING, 1, 4, 4), new Want(Items.FLOWER_POT, 2, 2, 8), new Want(Items.SUNFLOWER, 4, 3, 8),
			new Want(Items.CANDLE, 4, 3, 10), new Want(Items.APPLE, 8, 2, 12), new Want(Items.MELON_SLICE, 12, 2, 12),
			new Want(Items.FEATHER, 8, 2, 12), new Want(Items.LEAD, 1, 4, 4), new Want(Items.NAME_TAG, 1, 10, 2),
			new Want(Items.GOAT_HORN, 1, 10, 2), new Want(Items.HONEYCOMB, 4, 4, 8));
	}

	/** {@code v}'s own want, the same every time they're asked. */
	static Want of(Villager v) {
		wants();
		long seed = v.getUUID().getLeastSignificantBits() ^ v.getUUID().getMostSignificantBits();
		java.util.Random pick = new java.util.Random(seed);
		List<Want> fromTrade = new ArrayList<>();
		for (var entry : byTrade.entrySet()) {
			if (v.getVillagerData().profession().is(entry.getKey())) {
				fromTrade.addAll(entry.getValue());
			}
		}
		List<Want> nature = byNature.getOrDefault(McaCompat.personality(v), List.of());
		List<Want> from = !nature.isEmpty() && pick.nextFloat() < NATURE_SHARE ? nature
			: !fromTrade.isEmpty() && pick.nextFloat() < TRADE_SHARE ? fromTrade : anyone;
		return from.get(pick.nextInt(from.size()));
	}

	/** Puts {@code v}'s own want on their trades, once. */
	static void stock(Villager v) {
		Want w = of(v);
		MerchantOffers offers = v.getOffers();
		for (MerchantOffer offer : offers) {
			if (offer.getCostA().is(w.item()) && Currencies.isMoney(offer.getResult())) {
				return;
			}
		}
		offers.add(new MerchantOffer(new ItemCost(w.item(), w.count()), new ItemStack(Items.EMERALD, w.emeralds()), w.uses(), XP, PRICE_MULTIPLIER));
	}
}
