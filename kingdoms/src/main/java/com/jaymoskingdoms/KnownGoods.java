package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

/**
 * (Village mods patch 5, Andy's pick "goods each people is known for") Every land's towns are known for something of
 * their own: a dish of the land (Jaymo's Kitchen's, through {@link KitchenCompat#dish}; the plain food it's made from
 * without Kitchen) and a good of the land. A stranger isn't sold them. Trade with a town's people often enough
 * ({@link #DISH_AT} trades) and its traders start putting the dish on the counter for you, and after more
 * ({@link #GOOD_AT}) the good too; the one you trade with says so when it happens. Counted per player and town
 * ({@link #TRADES}), added to (and taken off) a trader's counter as the trade opens, the way {@link FrontierWear} does,
 * and priced in the town's coin like the rest ({@link Currencies#localize}). The land is the town's own
 * ({@link Palette#at} at its seat).
 */
public final class KnownGoods {
	// ===== the knobs ================================================================================================
	/** Trades with one town's people before its dish is on the counter for you, and before its good is too. */
	static final int DISH_AT = 10, GOOD_AT = 30;
	/** How many times each may be bought before the trader restocks, and the trader experience each gives. */
	static final int USES = 8, XP = 2;
	static final float PRICE_MULT = 0.05F;
	// ================================================================================================================

	/** One thing a land is known for: Kitchen's dish (or null), the plain stand-in, how many an emerald buys, and who sells it. */
	record Good(@Nullable String dish, Item plain, int count, int emeralds, ResourceKey<VillagerProfession> seller) {
		/** What it is in this world: Kitchen's dish when there is one, else the plain stand-in. */
		ItemStack stack() {
			Item kitchen = this.dish == null ? null : KitchenCompat.dish(this.dish);
			return new ItemStack(kitchen != null ? kitchen : this.plain, this.count);
		}
	}

	/** A land's dish and good. */
	record Known(String land, Good dish, Good good) {
	}

	private static final ResourceKey<VillagerProfession> FARMER = VillagerProfession.FARMER, BUTCHER = VillagerProfession.BUTCHER,
		FISHER = VillagerProfession.FISHERMAN, LEATHER = VillagerProfession.LEATHERWORKER, MASON = VillagerProfession.MASON,
		ARMORER = VillagerProfession.ARMORER, SHEPHERD = VillagerProfession.SHEPHERD;

	/**
	 * Each land's: the Heartlanders' plains (bread and honey for market day), the northern woods (smoked ham, berries for the
	 * berry wine), the snowfields (venison stew, packed ice), the savanna (kebabs, leather off the herds), the sands
	 * (melon juice where water is sold, cactus), the badlands (chili, gold from the mines), the jungle (chocolate, cocoa),
	 * the swamps (clam chowder, lily pads) and Blossom (salmon rolls, petals).
	 */
	static final Known PLAINS = new Known("plains", new Good("rustic_bread", Items.BREAD, 3, 1, FARMER), new Good(null, Items.HONEY_BOTTLE, 2, 1, FARMER)),
		TAIGA = new Known("taiga", new Good("smoked_ham", Items.COOKED_PORKCHOP, 2, 1, BUTCHER), new Good(null, Items.SWEET_BERRIES, 16, 1, FARMER)),
		SNOWY = new Known("snowy", new Good("venison_stew", Items.RABBIT_STEW, 1, 1, BUTCHER), new Good(null, Items.PACKED_ICE, 6, 1, MASON)),
		SAVANNA = new Known("savanna", new Good("mutton_kebab", Items.COOKED_MUTTON, 2, 1, BUTCHER), new Good(null, Items.LEATHER, 5, 1, LEATHER)),
		DESERT = new Known("desert", new Good("melon_juice", Items.MELON_SLICE, 2, 1, FARMER), new Good(null, Items.CACTUS, 8, 1, FARMER)),
		BADLANDS = new Known("badlands", new Good("chili_con_carne", Items.COOKED_BEEF, 2, 1, BUTCHER), new Good(null, Items.GOLD_NUGGET, 9, 2, ARMORER)),
		JUNGLE = new Known("jungle", new Good("chocolate_cookie", Items.COOKIE, 4, 1, FARMER), new Good(null, Items.COCOA_BEANS, 12, 1, FARMER)),
		SWAMP = new Known("swamp", new Good("clam_chowder", Items.COOKED_COD, 2, 1, FISHER), new Good(null, Items.LILY_PAD, 8, 1, FISHER)),
		BLOSSOM = new Known("blossom", new Good("salmon_roll", Items.COOKED_SALMON, 2, 1, FISHER), new Good(null, Items.PINK_PETALS, 12, 1, SHEPHERD));
	static final List<Known> ALL = List.of(PLAINS, TAIGA, SNOWY, SAVANNA, DESERT, BADLANDS, JUNGLE, SWAMP, BLOSSOM);

	/** On a player: trades done with each town's people (faction id → trades). Saved. */
	static final AttachmentType<Map<String, Integer>> TRADES = AttachmentRegistry.create(JaymosKingdoms.id("known_goods_trades"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

	private KnownGoods() {
	}

	/** What the land at {@code f}'s seat is known for. */
	static Known of(ServerLevel level, Faction f) {
		Palette p = Palette.at(level, f.seat());
		if (p == Palette.TAIGA) {
			return TAIGA;
		} else if (p == Palette.SNOWY) {
			return SNOWY;
		} else if (p == Palette.SAVANNA) {
			return SAVANNA;
		} else if (p == Palette.DESERT) {
			return DESERT;
		} else if (p == Palette.BADLANDS) {
			return BADLANDS;
		} else if (p == Palette.JUNGLE) {
			return JUNGLE;
		} else if (p == Palette.SWAMP) {
			return SWAMP;
		} else if (p == Palette.CHERRY) {
			return BLOSSOM;
		}
		return PLAINS;
	}

	/** How many trades {@code player} has done with {@code f}'s people. */
	static int trades(Player player, int faction) {
		return player.getAttachedOrElse(TRADES, Map.<String, Integer>of()).getOrDefault(Integer.toString(faction), 0);
	}

	/** VillagerTradeMixin: {@code player} traded with {@code v}; on reaching a step, {@code v} says what the town now sells them. */
	public static void traded(Villager v, ServerPlayer player) {
		if (!(v.level() instanceof ServerLevel level)) {
			return;
		}
		Faction f = Quests.settlement(level, v);
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
			return;
		}
		int before = trades(player, f.id());
		Map<String, Integer> all = new HashMap<>(player.getAttachedOrElse(TRADES, Map.of()));
		all.put(Integer.toString(f.id()), before + 1);
		player.setAttached(TRADES, Map.copyOf(all));
		Known known = of(level, f);
		Good reached = before + 1 == DISH_AT ? known.dish() : before + 1 == GOOD_AT ? known.good() : null;
		if (reached != null) {
			Speech.tell(level, v, player, Component.translatable("known.jaymoskingdoms.unlocked", f.name(), reached.stack().getHoverName()), Speech.GOLD);
		}
	}

	/** VillagerPriceMixin, as the trade opens: the town's dish and good on {@code v}'s counter for {@code player} if earned. */
	public static void offers(Villager v, Player player) {
		if (!(v.level() instanceof ServerLevel level) || v.isBaby()) {
			return;
		}
		Faction f = Quests.settlement(level, v);
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
			return;
		}
		MerchantOffers offers = v.getOffers();
		int done = trades(player, f.id());
		Known known = of(level, f);
		show(v, offers, known.dish(), done >= DISH_AT);
		show(v, offers, known.good(), done >= GOOD_AT);
	}

	/** {@code good} on {@code v}'s counter if {@code earned} and {@code v} sells it, else off it. */
	private static void show(Villager v, MerchantOffers offers, Good good, boolean earned) {
		ItemStack stack = good.stack();
		boolean sells = v.getVillagerData().profession().is(good.seller());
		boolean there = offers.stream().anyMatch(o -> is(o, good, stack));
		if (earned && sells && !there) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, good.emeralds()), stack, USES, XP, PRICE_MULT));
		} else if ((!earned || !sells) && there) {
			offers.removeIf(o -> is(o, good, stack));
		}
	}

	/** {@code o} is this good's offer: its result and count, bought with emeralds or the coin they were turned into. */
	private static boolean is(MerchantOffer o, Good good, ItemStack stack) {
		ItemStack result = o.getResult();
		return result.is(stack.getItem()) && result.getCount() == stack.getCount() && o.getMaxUses() == USES
			&& o.getXp() == XP;
	}

	/** A line for the town's page ({@link TownDoings}): what the town is known for, and what {@code player} has been sold of it. */
	static Component line(ServerLevel level, Faction f, Player player) {
		Known known = of(level, f);
		int done = trades(player, f.id());
		Component dish = known.dish().stack().getHoverName(), good = known.good().stack().getHoverName();
		if (done >= GOOD_AT) {
			return Component.translatable("known.jaymoskingdoms.page.all", dish, good);
		}
		if (done >= DISH_AT) {
			return Component.translatable("known.jaymoskingdoms.page.dish", dish, good, GOOD_AT - done);
		}
		return Component.translatable("known.jaymoskingdoms.page.none", dish, good, DISH_AT - done);
	}
}
