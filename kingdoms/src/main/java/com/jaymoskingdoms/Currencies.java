package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.484) Currencies, Andy (2026-11-03): "Diversify the currencies, it can change based on the prominent trading hub's
 * currency or other factors, like superior." The five coins of the realms ({@link #REALM_SILVER}, {@link #HACKSILVER},
 * {@link #GOLD_DINAR}, {@link #SHELL_STRING}, {@link #MON_COIN}), beside the emerald every trader knows
 * ({@link Currency}).
 * <ul>
 * <li><b>Which coin</b> ({@link #of}): a camp's own (greenskins' bent ingots, pirates' gold, anyone else's emeralds); a
 * vassal fiefdom its overlord's; a kingdom its land's ({@link #culture}: plains silver, northern hacksilver, southern
 * dinars, wildwood shells, Blossom mon) unless a much richer kingdom nearby is the trading hub, and then the hub's; a
 * village or haven the hub's, or its land's where there is none. It is worked out afresh as the hubs rise and fall.</li>
 * <li><b>Prices</b> ({@link #localize}): a trader's emerald prices are set in the coin of the town it stands in, at the
 * standard rate, whenever a player opens the trade; the emerald is the settlement's unit of account (its treasury,
 * tolls and bounties stay counted in it, so old saves hold), paid out in the local coin ({@link #coinsFor}).</li>
 * <li><b>Money-changers</b> ({@link #staff}): a big enough town takes on a changer, whose counter swaps any coin for
 * any other at the going rate ({@link #rate}), which moves with how rich the realms behind each coin are.</li>
 * </ul>
 */
public final class Currencies {
	// ================================================================================================================
	// Tuning
	/** How far (blocks) a kingdom can be the trading hub of a settlement, and how fast its pull fades with distance. */
	static final double HUB_RANGE = 1400.0, HUB_FALLOFF = 500.0;
	/** A hub's pull must beat the settlement's own worth by this many times before it changes coin: a kingdom, a village, a Blossom village (proud of its mon). */
	static final double REALM_STICK = 1.6, VILLAGE_STICK = 1.15, BLOSSOM_STICK = 3.0;
	/** How long (ticks) a settlement's currency, and the rates, are trusted before they are worked out again. */
	static final long RESOLVE_TTL = 1200L, RATE_TTL = 600L;
	/** The rate of a realm's coin: this much of its standard at worst, up to this much more as its realms are richer than the average. */
	static final double RATE_FLOOR = 0.92, RATE_SWING = 0.09, RATE_CEIL = 1.10;
	/** A money-changer keeps this share of what is changed. */
	static final double SPREAD = 0.06;
	/** A money-changer's offers: the worth (emeralds) of the coin a trade pays out, their uses before restocking. */
	static final int CHANGE_WORTH = 6, CHANGE_USES = 16;
	/** A money-changer's counter restocks once this many days have passed (it has no job site to restock at). */
	static final long CHANGE_RESTOCK_DAYS = 1L;
	/** Money-changers: a town needs this many folk to have one, and another for every this many; at most this many. */
	static final int CHANGER_POPULATION = 8, CHANGER_PER = 24, CHANGERS_MOST = 2;
	/** How often (ticks) towns are looked at for a changer, and how far from the seat one is looked for. */
	static final int STAFF_EVERY = 400;
	static final double STAFF_RANGE = Economy.POPULATION_RANGE;
	/** A kingdom's taxes: this share (percent) is also paid into the storehouse in its coin, for the town's own wages. */
	static final int TAX_COIN_PERCENT = 20;
	/**
	 * (0.7.484) Coin is only kept in a storehouse up to this much worth (emeralds), the town's wage reserve: past it, the
	 * coined share of the taxes stays in the treasury, so coin doesn't pile up and crowd the storehouse.
	 */
	static final int TAX_COIN_RESERVE = 48;
	/** (0.7.484) A payout or price split into coin and emerald change may be off its worth by this share at most, to use more of the town's coin. */
	static final double PAY_SLACK = 0.01;
	/** (0.7.484) How many coin counts below the most are tried when a sum is split into coin and emerald change. */
	static final int PAY_TRIES = 12;
	/**
	 * (0.7.484) A price is put in a coin only when that many coins come within this share of its worth (asked: never under
	 * it; paid: never over it); else it stays in emeralds, or its own coin, so rounding to a big coin never makes money.
	 */
	static final double ROUND_SLACK = 0.10;
	/** (0.7.484) How many offers' worths a trader keeps, those in its list now first, then those put away for a while (TierGate). */
	static final int BASES_KEPT = 64;
	// ================================================================================================================

	public static Item REALM_SILVER, HACKSILVER, GOLD_DINAR, SHELL_STRING, MON_COIN;

	/**
	 * (0.7.484) A trader's offer as it was before any town's coin was put on it: its place in the trader's list when last
	 * set (only a note), the offer as last set (how it is known again, wherever it has moved to in the list or if it was
	 * put away and brought back), the worth of each of its coin slots (cost A, cost B, result) in thousandths of an emerald
	 * (0 where the slot isn't coin), the coin each slot first was ({@link Currency#id}, "" where unknown), and how many of
	 * each slot's goods it first was (gb 0 where it had no cost B; -1 where unknown), as the goods side may be scaled to
	 * land on whole coins. Prices are
	 * always worked out from this, never from the last town's coin, so they don't creep as a town changes coin back and forth.
	 */
	record Base(int index, String print, int a, int b, int r, String ca, String cb, String cr, int ga, int gb, int gr) {
		static final Codec<Base> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("index").forGetter(Base::index),
				Codec.STRING.fieldOf("print").forGetter(Base::print),
				Codec.INT.fieldOf("a").forGetter(Base::a),
				Codec.INT.fieldOf("b").forGetter(Base::b),
				Codec.INT.fieldOf("r").forGetter(Base::r),
				Codec.STRING.optionalFieldOf("ca", "").forGetter(Base::ca),
				Codec.STRING.optionalFieldOf("cb", "").forGetter(Base::cb),
				Codec.STRING.optionalFieldOf("cr", "").forGetter(Base::cr),
				Codec.INT.optionalFieldOf("ga", -1).forGetter(Base::ga),
				Codec.INT.optionalFieldOf("gb", -1).forGetter(Base::gb),
				Codec.INT.optionalFieldOf("gr", -1).forGetter(Base::gr))
			.apply(i, Base::new));
	}

	/** (0.7.484) A trader's offers in their own coin ({@link Base}), the ones with coin in them. */
	static final AttachmentType<List<Base>> BASES = AttachmentRegistry.create(JaymosKingdoms.id("trade_bases"),
		b -> b.persistent(Base.CODEC.listOf()));
	/** (0.7.484) The day a money-changer's counter last restocked. */
	static final AttachmentType<Long> CHANGER_DAY = AttachmentRegistry.create(JaymosKingdoms.id("changer_day"), b -> b.persistent(Codec.LONG));

	/** Faction id → (its coin, when it was worked out). */
	private static final Map<Integer, Object[]> RESOLVED = new HashMap<>();
	/** Faction id → the climate of its seat (never changes). */
	private static final Map<Integer, Climate> CLIMATES = new HashMap<>();
	/** The rates: (game time, the factor of each human coin). */
	private static long ratesAt = -RATE_TTL;
	private static final Map<Currency, Double> FACTORS = new EnumMap<>(Currency.class);

	private Currencies() {
	}

	static void register() {
		REALM_SILVER = item("realm_silver");
		HACKSILVER = item("hacksilver");
		GOLD_DINAR = item("gold_dinar");
		SHELL_STRING = item("shell_string");
		MON_COIN = item("mon_coin");
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(REALM_SILVER);
			output.accept(HACKSILVER);
			output.accept(GOLD_DINAR);
			output.accept(SHELL_STRING);
			output.accept(MON_COIN);
		});
		ServerTickEvents.END_SERVER_TICK.register(Currencies::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("currency").executes(Currencies::report))));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RESOLVED.clear();
			CLIMATES.clear();
			FACTORS.clear();
			ratesAt = -RATE_TTL;
		});
	}

	private static Item item(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new HavenWares.Ware(new Item.Properties().setId(key), name));
	}

	// ---- which coin ----------------------------------------------------------------------------------------------------

	/** The coin {@code f} trades in (emeralds for none). Worked out afresh every {@link #RESOLVE_TTL} ticks. */
	public static Currency of(MinecraftServer server, @Nullable Faction f) {
		if (f == null) {
			return Currency.EMERALD;
		}
		long now = Factions.level(server).getGameTime();
		Object[] known = RESOLVED.get(f.id());
		if (known != null && now - (Long)known[1] < RESOLVE_TTL) {
			return (Currency)known[0];
		}
		Currency c = resolve(server, f);
		RESOLVED.put(f.id(), new Object[] {c, now});
		return c;
	}

	private static Currency resolve(MinecraftServer server, Faction f) {
		if (f.type() == FactionType.BARBARIAN) {
			return Hordes.greenskin(server, f.id()) ? Currency.BENT : PirateCoves.is(server, f.id()) ? Currency.GOLD : Currency.EMERALD;
		}
		// a vassal takes its superior's coin, up the chain
		Faction at = f;
		for (int hop = 0; hop < 4 && at.type() == FactionType.FIEFDOM && at.overlord() >= 0; hop++) {
			Faction up = Factions.get(server, at.overlord());
			if (up == null || up.fallen() || up.id() == at.id()) {
				break;
			}
			at = up;
		}
		Currency own = culture(server, at);
		Faction hub = null;
		double best = 0.0;
		for (Faction k : Factions.all(server, FactionType.KINGDOM)) {
			if (k.fallen() || k.id() == at.id() || Havens.is(server, k)) {
				continue;
			}
			double d = Raids.horizontal(k.seat(), at.seat());
			if (d > HUB_RANGE) {
				continue;
			}
			double pull = Wealth.worth(server, k) / (1.0 + d / HUB_FALLOFF);
			if (pull > best) {
				best = pull;
				hub = k;
			}
		}
		double stick = own == Currency.MON ? BLOSSOM_STICK : at.type() == FactionType.KINGDOM ? REALM_STICK : VILLAGE_STICK;
		if (hub != null && best > (Math.max(0L, Wealth.worth(server, at)) + 1.0) * stick) {
			return culture(server, hub);
		}
		return own;
	}

	/** The coin of the land {@code f} stands on. */
	static Currency culture(MinecraftServer server, Faction f) {
		Climate climate = CLIMATES.computeIfAbsent(f.id(), id -> Climate.at(Factions.level(server), f.seat()));
		return switch (climate) {
			case CHERRY -> Currency.MON;
			case TAIGA, SNOWY -> Currency.HACKSILVER;
			case SAVANNA, DESERT, BADLANDS -> Currency.DINAR;
			case JUNGLE, SWAMP -> Currency.SHELLS;
			case PLAINS -> Currency.SILVER;
		};
	}

	// ---- the going rate -------------------------------------------------------------------------------------------------

	/** One {@code c} coin's worth now, in thousandths of an emerald: its standard, moved by how rich the realms that use it are. */
	public static int rate(MinecraftServer server, Currency c) {
		if (!c.human() || c == Currency.EMERALD) {
			return c.base;
		}
		long now = Factions.level(server).getGameTime();
		if (now - ratesAt >= RATE_TTL || FACTORS.isEmpty()) {
			ratesAt = now;
			Map<Currency, Long> wealth = new EnumMap<>(Currency.class);
			long total = 0L;
			for (Faction k : Factions.all(server, FactionType.KINGDOM)) {
				if (k.fallen() || Havens.is(server, k)) {
					continue;
				}
				long w = Math.max(0L, Wealth.worth(server, k));
				wealth.merge(of(server, k), w, Long::sum);
				total += w;
			}
			int coins = 0;
			for (Currency x : Currency.values()) {
				coins += x.human() && x != Currency.EMERALD ? 1 : 0;
			}
			double mean = total / (double)coins;
			for (Currency x : Currency.values()) {
				double ratio = mean <= 0.0 ? 1.0 : wealth.getOrDefault(x, 0L) / mean;
				FACTORS.put(x, Mth.clamp(RATE_FLOOR + RATE_SWING * ratio, RATE_FLOOR, RATE_CEIL));
			}
		}
		return Math.max(1, (int)Math.round(c.base * FACTORS.getOrDefault(c, 1.0)));
	}

	// ---- amounts -----------------------------------------------------------------------------------------------------------

	/** {@code stack} is one of the coins a human trader takes (an emerald or a realm's coin). */
	public static boolean isMoney(ItemStack stack) {
		Currency c = stack.isEmpty() ? null : Currency.byItem(stack.getItem());
		return c != null && c.human();
	}

	/** {@code emeralds} of worth as a count of {@code c} coins, rounded up (what a buyer is asked for). */
	public static int coinsFor(Currency c, long emeralds) {
		return (int)Math.min(Integer.MAX_VALUE, Math.max(1L, (emeralds * 1000L + c.base - 1L) / c.base));
	}

	/**
	 * (0.7.484) A sum as it is handed over: whole coins of {@code coin}, and the change in emeralds. What is paid out
	 * ({@link #pay}) is never worth more than is owed, and what is asked ({@link #owe}) never less, so no rounding to a big
	 * coin makes money.
	 */
	public record Pay(Currency coin, long coins, long emeralds) {
		/** What it is worth, in thousandths of an emerald. */
		public long milli() {
			return this.coins * this.coin.base + this.emeralds * 1000L;
		}

		public boolean isEmpty() {
			return this.coins <= 0L && this.emeralds <= 0L;
		}

		/** As stacks: the coin first, then the emeralds. */
		public List<ItemStack> stacks() {
			List<ItemStack> out = new ArrayList<>();
			for (long left = this.coins; left > 0L; left -= 64L) {
				out.add(new ItemStack(this.coin.item(), (int)Math.min(64L, left)));
			}
			for (long left = this.emeralds; left > 0L; left -= 64L) {
				out.add(new ItemStack(Items.EMERALD, (int)Math.min(64L, left)));
			}
			return out;
		}
	}

	/** (0.7.484) {@code milli} of worth paid out in {@code c}: as many whole coins as fit, the change in emeralds, never more than the worth. */
	public static Pay pay(Currency c, long milli) {
		return split(c, milli, false, Long.MAX_VALUE);
	}

	/** (0.7.484) {@code emeralds} of worth paid out in {@code c} ({@link #pay}). */
	public static Pay payEmeralds(Currency c, long emeralds) {
		return pay(c, emeralds * 1000L);
	}

	/** (0.7.484) {@code milli} of worth asked in {@code c}: whole coins, the rest in emeralds, never less than the worth. */
	public static Pay owe(Currency c, long milli) {
		return split(c, milli, true, Long.MAX_VALUE);
	}

	/**
	 * (0.7.484) {@code milli} split into at most {@code most} of {@code c}'s coins and emerald change, over the worth if
	 * {@code over} (asked), else under it (paid): the exact split with the most coins, or if there is none, of those
	 * within {@link #PAY_SLACK} of the worth (or as near as any comes), the one with the most coins.
	 */
	static Pay split(Currency c, long milli, boolean over, long most) {
		if (milli <= 0L) {
			return new Pay(c, 0L, 0L);
		}
		if (c == Currency.EMERALD) {
			return new Pay(c, 0L, over ? (milli + 999L) / 1000L : milli / 1000L);
		}
		long top = Math.min(most, over ? (milli + c.base - 1L) / c.base : milli / c.base);
		long low = Math.max(0L, top - PAY_TRIES);
		long least = Long.MAX_VALUE;
		for (long k = top; k >= low; k--) {
			least = Math.min(least, splitError(c, milli, over, k));
		}
		least = Math.min(least, splitError(c, milli, over, 0L));
		// (an exact split always wins; only failing one, a little off for more of the town's coin)
		long allowed = least == 0L ? 0L : Math.max(least, (long)(milli * PAY_SLACK));
		long k = 0L;
		for (long at = top; at >= low; at--) {
			if (splitError(c, milli, over, at) <= allowed) {
				k = at;
				break;
			}
		}
		long rest = Math.max(0L, milli - k * c.base);
		return new Pay(c, k, over ? (rest + 999L) / 1000L : rest / 1000L);
	}

	private static long splitError(Currency c, long milli, boolean over, long k) {
		long rest = Math.max(0L, milli - k * c.base);
		long e = over ? (rest + 999L) / 1000L : rest / 1000L;
		return Math.abs(k * c.base + e * 1000L - milli);
	}

	/** {@code n} of {@code c}'s coins, as shown ("12 Realm Silver"). */
	public static Component amount(Currency c, long n) {
		return Component.literal(n + " ").append(new ItemStack(c.item()).getHoverName());
	}

	/** (0.7.484) {@code p} as shown: "12 Realm Silver", or with its change, "1 Gold Dinar, 1 Emerald". */
	public static Component amount(Pay p) {
		if (p.coin() == Currency.EMERALD || p.coins() <= 0L && p.emeralds() > 0L) {
			return amount(Currency.EMERALD, p.emeralds() + (p.coin() == Currency.EMERALD ? p.coins() : 0L));
		}
		if (p.emeralds() <= 0L) {
			return amount(p.coin(), p.coins());
		}
		return amount(p.coin(), p.coins()).copy().append(Component.literal(", ")).append(amount(Currency.EMERALD, p.emeralds()));
	}

	/** (0.7.484) Gives {@code player} {@code p}: its coins and its change. */
	public static void give(ServerPlayer player, Pay p) {
		give(player, p.coin(), p.coins());
		give(player, Currency.EMERALD, p.emeralds());
	}

	/** (0.7.484) The worth (thousandths of an emerald) {@code player} carries that {@code f} takes: its coin and emeralds. */
	public static long purse(MinecraftServer server, ServerPlayer player, @Nullable Faction f) {
		Currency c = of(server, f);
		long milli = Purse.count(player, Items.EMERALD) * 1000L;
		if (c != Currency.EMERALD && c.human()) {
			milli += Purse.count(player, c.item()) * (long)c.base;
		}
		return milli;
	}

	/** (0.7.484) {@code player} carries {@code emeralds} of worth that {@code f} takes ({@link #purse}). */
	public static boolean canPay(MinecraftServer server, ServerPlayer player, @Nullable Faction f, long emeralds) {
		return purse(server, player, f) >= emeralds * 1000L;
	}

	/** (0.7.484) The worth (thousandths of an emerald) of the realms' coin in {@code f}'s storehouse (not its emeralds, which are goods as much as coin). */
	static long stored(ServerLevel level, Faction f) {
		long milli = 0L;
		for (Currency x : Currency.values()) {
			if (x.human() && x != Currency.EMERALD) {
				milli += Storage.count(level, f, x.item()) * (long)x.base;
			}
		}
		return milli;
	}

	/** (0.7.484) {@code emeralds} of worth as {@code f} asks it ({@link #owe}), as shown. */
	public static Component asked(MinecraftServer server, @Nullable Faction f, long emeralds) {
		return amount(owe(of(server, f), emeralds * 1000L));
	}

	/** Gives {@code player} {@code n} of {@code c}'s coins (what doesn't fit in the pack drops at their feet). */
	public static void give(ServerPlayer player, Currency c, long n) {
		for (long left = n; left > 0L; left -= 64L) {
			player.getInventory().placeItemBackInInventory(new ItemStack(c.item(), (int)Math.min(64L, left)), net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	/**
	 * Takes up to {@code emeralds} of worth from {@code player} as a fine: the coin of {@code f} first, then emeralds (every
	 * trader takes those). Returns the worth taken.
	 */
	public static int fine(MinecraftServer server, ServerPlayer player, @Nullable Faction f, int emeralds) {
		Currency c = of(server, f);
		// (0.7.484) counted in thousandths of an emerald, so a few small coins count for what they are worth: as many whole
		// coins as fit in the worth, the rest in emeralds, and only when short of emeralds one more coin
		long owed = emeralds * 1000L, taken = 0L;
		boolean coin = c != Currency.EMERALD && c.human();
		int have = coin ? Purse.count(player, c.item()) : 0;
		int coins = (int)Math.min(have, owed / Math.max(1, c.base));
		if (coins > 0) {
			Purse.take(player, c.item(), coins);
			taken = coins * (long)c.base;
		}
		long rest = owed - taken;
		if (rest > 0L) {
			int n = (int)Math.min((rest + 999L) / 1000L, Purse.count(player, Items.EMERALD));
			if (n > 0) {
				Purse.take(player, Items.EMERALD, n);
				taken += n * 1000L;
			}
		}
		rest = owed - taken;
		if (rest > 0L && have > coins) {
			int more = (int)Math.min(have - coins, (rest + c.base - 1L) / c.base);
			Purse.take(player, c.item(), more);
			taken += more * (long)c.base;
		}
		// (the worth taken, to the nearest emerald)
		return (int)Math.min(emeralds, (taken + 500L) / 1000L);
	}

	/** {@code stack}'s worth (emeralds), if it is a human coin; 0 for anything else. Used to count a trade for its toll. */
	public static long worthOf(ItemStack stack) {
		Currency c = stack.isEmpty() ? null : Currency.byItem(stack.getItem());
		return c != null && c.human() ? stack.getCount() * (long)c.base / 1000L : 0L;
	}

	/** (pirates) {@code stack} is gold in some shape: ingot, nugget, block, raw, ornament or a dinar. */
	public static boolean gold(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(GOLD_DINAR)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.contains("gold") && !path.contains("golden_apple") && !path.contains("golden_carrot") && !path.contains("glistering");
	}

	/** (pirates) What a gold {@code stack} fetches them, in emeralds (nuggets a ninth of an ingot; any other gold thing, an ingot's worth). */
	public static long goldWorth(ItemStack stack) {
		if (!gold(stack)) {
			return 0L;
		}
		long each = stack.is(GOLD_DINAR) ? Currency.DINAR.base : stack.is(Items.GOLD_NUGGET) ? Currency.GOLD.base / 9
			: stack.is(Items.GOLD_BLOCK) ? Currency.GOLD.base * 9L : Currency.GOLD.base;
		return stack.getCount() * each / 1000L;
	}

	// ---- prices ---------------------------------------------------------------------------------------------------------------

	/**
	 * (from {@code VillagerPriceMixin}, last, when {@code player} opens a trade with {@code villager}) a town trader's
	 * emerald (or other realm's coin) prices are set in the coin of the town it stands in; a money-changer's offers are
	 * made afresh at the going rate.
	 */
	public static void localize(Villager villager, Player player) {
		if (!(villager.level() instanceof ServerLevel level) || villager.isBaby()) {
			return;
		}
		Faction f = Quests.settlement(level, villager);
		if (f == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		Currency c = of(server, f);
		if (changer(villager)) {
			counter(level, villager, c);
			return;
		}
		if (Underworld.blackMarket(villager) || !c.human()) {
			return;
		}
		// (0.7.484) each price worked out from the offer's own worth (Base), never from the coin it was last set in; a Base is
		// found by the offer as it was last set (its print), not by its place, since TierGate moves offers about and puts them away
		Map<String, ArrayDeque<Base>> known = new HashMap<>();
		List<Base> stored = villager.getAttachedOrElse(BASES, List.<Base>of());
		for (Base base : stored) {
			known.computeIfAbsent(base.print(), k -> new ArrayDeque<>()).add(base);
		}
		List<Base> now = new ArrayList<>();
		MerchantOffers offers = villager.getOffers();
		for (int i = 0; i < offers.size(); i++) {
			MerchantOffer o = offers.get(i);
			int wa = worth(o.getItemCostA()), wb = o.getItemCostB().map(Currencies::worth).orElse(0), wr = worth(o.getResult());
			if (wa == 0 && wb == 0 && wr == 0) {
				continue;
			}
			String had = print(o.getItemCostA(), o.getItemCostB(), o.getResult());
			ArrayDeque<Base> same = known.get(had);
			Base base = same == null ? null : same.poll();
			if (base == null) {
				// (new, or not an offer it knows: what it asks now is its worth, in the coin it asks it in)
				base = new Base(i, "", wa, wb, wr, coinId(o.getItemCostA().item().value()),
					o.getItemCostB().map(x -> coinId(x.item().value())).orElse(""), coinId(o.getResult().getItem()),
					o.getItemCostA().count(), o.getItemCostB().map(ItemCost::count).orElse(0), o.getResult().getCount());
			}
			Base was = base;
			// (0.7.484) the goods as they first were, before any scaling to a town's coin
			ItemCost goodsA = was.a() > 0 ? o.getItemCostA() : recount(o.getItemCostA(), was.ga());
			Optional<ItemCost> goodsB = was.b() > 0 ? o.getItemCostB() : was.gb() == 0 ? Optional.empty() : o.getItemCostB().map(x -> recount(x, was.gb()));
			ItemStack goodsR = was.r() > 0 ? o.getResult() : recount(o.getResult(), was.gr());
			ItemCost a = was.a() > 0 ? asked(was.a(), c, was.ca(), o.getItemCostA()) : goodsA;
			Optional<ItemCost> b = was.b() > 0 ? o.getItemCostB().map(x -> asked(was.b(), c, was.cb(), x)) : goodsB;
			ItemStack result = was.r() > 0 ? paid(was.r(), c, was.cr(), o.getResult()) : goodsR;
			// (0.7.484) a price that won't land on whole town coins: the goods are scaled until it does (2 hacksilver for 16
			// wheat, not 1 emerald for 20), or else the rest is asked as emerald change in the empty second slot
			boolean single = was.b() == 0 && was.gb() == 0;
			if (single && was.a() > 0 && was.r() == 0 && a.item().value() != c.item() && !goodsR.isEmpty()) {
				int[] scaled = scale(was.a(), c, goodsR.getCount(), goodsR.getMaxStackSize(), true);
				if (scaled != null) {
					a = new ItemCost(c.item(), scaled[0]);
					result = goodsR.copyWithCount(scaled[1]);
				} else {
					Pay owed = split(c, was.a(), true, 64L);
					if (owed.coins() >= 1L && owed.emeralds() >= 1L && owed.emeralds() <= 64L && near(owed.milli(), was.a(), true)) {
						a = new ItemCost(c.item(), (int)owed.coins());
						b = Optional.of(new ItemCost(Items.EMERALD, (int)owed.emeralds()));
					}
				}
			} else if (single && was.r() > 0 && was.a() == 0 && !result.is(c.item())) {
				int[] scaled = scale(was.r(), c, goodsA.count(), goodsA.itemStack().getMaxStackSize(), false);
				if (scaled != null) {
					result = new ItemStack(c.item(), scaled[0]);
					a = recount(goodsA, scaled[1]);
				}
			}
			String print = print(a, b, result);
			now.add(new Base(i, print, was.a(), was.b(), was.r(), was.ca(), was.cb(), was.cr(), was.ga(), was.gb(), was.gr()));
			// (0.7.484) vanilla's discount (and Purists' and Prejudice's) is a count of whatever cost A is now, set afresh on
			// every open: it is put back in the goods or coin it was first asked in, then into cost A as it now is, every time
			int diff = discount(o.getSpecialPriceDiff(), was, o.getItemCostA(), a);
			if (print.equals(had)) {
				if (diff != o.getSpecialPriceDiff()) {
					o.setSpecialPriceDiff(diff);
				}
				continue;
			}
			MerchantOffer fresh = new MerchantOffer(a, b, result, o.getUses(), o.getMaxUses(), o.getXp(), o.getPriceMultiplier(), o.getDemand());
			fresh.setSpecialPriceDiff(diff);
			offers.set(i, fresh);
		}
		// (the worths of offers put away for now are kept, so they are known again when they come back)
		for (Base base : stored) {
			ArrayDeque<Base> left = known.get(base.print());
			if (now.size() >= BASES_KEPT) {
				break;
			}
			if (left != null && left.remove(base)) {
				now.add(base);
			}
		}
		if (!now.isEmpty()) {
			villager.setAttached(BASES, List.copyOf(now));
		} else if (villager.hasAttached(BASES)) {
			villager.removeAttached(BASES);
		}
	}

	/**
	 * (0.7.484) A discount (or mark-up) of {@code diff} counted as though cost A were still as it was first asked ({@code was}),
	 * as a count of {@code a}: coin by worth (each of the coin it was first asked in),
	 * goods by count. Where the first count isn't known, {@code now} (cost A as it is) stands for it.
	 */
	private static int discount(int diff, Base was, ItemCost now, ItemCost a) {
		if (diff == 0) {
			return 0;
		}
		Currency to = Currency.byItem(a.item().value());
		if (was.a() > 0 && to != null && to.human()) {
			Currency from = byId(was.ca());
			float each = from != null && from.human() ? from.base : was.ga() > 0 ? (float)was.a() / was.ga() : 0.0F;
			return each <= 0.0F ? diff : Math.round(diff * each / to.base);
		}
		int first = was.ga() > 0 ? was.ga() : now.count();
		return first <= 0 ? diff : Math.round(diff * (float)a.count() / first);
	}

	/** (0.7.484) {@code cost} with {@code n} of its goods ({@code n} of 0 or less: as it is). */
	private static ItemCost recount(ItemCost cost, int n) {
		return n <= 0 || n == cost.count() ? cost : new ItemCost(cost.item(), n, cost.components());
	}

	private static ItemStack recount(ItemStack stack, int n) {
		return n <= 0 || n == stack.getCount() || stack.isEmpty() ? stack : stack.copyWithCount(n);
	}

	/**
	 * (0.7.484) {@code goods} goods worth {@code milli} traded instead as {coins, goods} of {@code c}'s coin, the price per
	 * good within {@link #ROUND_SLACK} of what it was (asked: never under it; paid: never over it), at most a stack of each:
	 * the nearest price, then the count of goods nearest what it was. Null if none comes near.
	 */
	static int @Nullable [] scale(long milli, Currency c, int goods, int maxStack, boolean over) {
		if (milli <= 0L || goods <= 0 || !c.human()) {
			return null;
		}
		int most = Math.min(64, Math.max(1, maxStack));
		int[] best = null;
		double bestErr = Double.MAX_VALUE;
		int bestOff = Integer.MAX_VALUE;
		for (int n = 1; n <= 64; n++) {
			long coin = (long)n * c.base * goods;
			long m = over ? coin / milli : (coin + milli - 1L) / milli;
			if (m < 1L || m > most) {
				continue;
			}
			double err = Math.abs(coin - milli * m) / (double)(milli * m);
			if (err > ROUND_SLACK) {
				continue;
			}
			int off = (int)Math.abs(m - goods);
			if (err < bestErr - 1.0E-3 || Math.abs(err - bestErr) <= 1.0E-3 && off < bestOff) {
				best = new int[] {n, (int)m};
				bestErr = Math.min(err, bestErr);
				bestOff = off;
			}
		}
		return best;
	}

	/** {@code cost}'s worth in thousandths of an emerald, if it is a human coin; else 0. */
	private static int worth(ItemCost cost) {
		Currency from = Currency.byItem(cost.item().value());
		return from == null || !from.human() ? 0 : cost.count() * from.base;
	}

	private static int worth(ItemStack stack) {
		Currency from = stack.isEmpty() ? null : Currency.byItem(stack.getItem());
		return from == null || !from.human() ? 0 : stack.getCount() * from.base;
	}

	/** An offer's three slots, as items and counts (to know an offer again). */
	private static String print(ItemCost a, Optional<ItemCost> b, ItemStack result) {
		return BuiltInRegistries.ITEM.getKey(a.item().value()) + "*" + a.count() + "|"
			+ b.map(x -> BuiltInRegistries.ITEM.getKey(x.item().value()) + "*" + x.count()).orElse("-") + "|"
			+ BuiltInRegistries.ITEM.getKey(result.getItem()) + "*" + result.getCount();
	}

	/** {@code item}'s {@link Currency#id} if it is a human coin, else "". */
	private static String coinId(Item item) {
		Currency from = Currency.byItem(item);
		return from == null || !from.human() ? "" : from.id();
	}

	private static @Nullable Currency byId(String id) {
		for (Currency x : Currency.values()) {
			if (x.id().equals(id)) {
				return x;
			}
		}
		return null;
	}

	/** (0.7.484) {@code coins} (thousandths of an emerald) is within {@link #ROUND_SLACK} of {@code milli}: over it if {@code over}, else under it. */
	public static boolean near(long coins, long milli, boolean over) {
		return over ? coins >= milli && coins <= milli * (1.0 + ROUND_SLACK) : coins <= milli && coins >= milli * (1.0 - ROUND_SLACK);
	}

	/**
	 * {@code milli} of worth asked in {@code c}'s coin (rounded up); else in emeralds; else in the coin {@code first} it was
	 * first asked in; else {@code fallback}. A coin is used only for a stack or less, and when it comes near the worth.
	 */
	private static ItemCost asked(int milli, Currency c, String first, ItemCost fallback) {
		for (Currency x : new Currency[] {c, Currency.EMERALD, byId(first)}) {
			if (x == null || !x.human()) {
				continue;
			}
			long n = (milli + x.base - 1L) / x.base;
			if (n >= 1L && n <= 64L && near(n * x.base, milli, true)) {
				return new ItemCost(x.item(), (int)n);
			}
		}
		return fallback;
	}

	/** {@code milli} of worth paid in {@code c}'s coin (rounded down), else in emeralds, else in its first coin, as for {@link #asked}; else {@code fallback}. */
	private static ItemStack paid(int milli, Currency c, String first, ItemStack fallback) {
		for (Currency x : new Currency[] {c, Currency.EMERALD, byId(first)}) {
			if (x == null || !x.human()) {
				continue;
			}
			long n = milli / x.base;
			if (n >= 1L && n <= 64L && near(n * x.base, milli, false)) {
				return new ItemStack(x.item(), (int)n);
			}
		}
		return fallback;
	}

	// ---- money-changers --------------------------------------------------------------------------------------------------------

	public static boolean changer(Villager v) {
		return v.getVillagerData().profession().value() == Professions.CHANGER;
	}

	/**
	 * (0.7.484) A changer's counter set at today's rates in town coin {@code local}, each trade keeping the uses it has had
	 * since it last restocked (once every {@link #CHANGE_RESTOCK_DAYS}), so opening the trade again doesn't restock it.
	 */
	private static void counter(ServerLevel level, Villager v, Currency local) {
		if (!local.human()) {
			return;
		}
		long day = Quests.day(level);
		Long last = v.getAttached(CHANGER_DAY);
		boolean restock = last == null || day - last >= CHANGE_RESTOCK_DAYS;
		MerchantOffers fresh = changes(level.getServer(), local);
		if (!restock) {
			for (int i = 0; i < fresh.size(); i++) {
				MerchantOffer o = fresh.get(i);
				for (MerchantOffer had : v.getOffers()) {
					if (had.getItemCostA().item().value() == o.getItemCostA().item().value() && had.getResult().is(o.getResult().getItem())) {
						fresh.set(i, new MerchantOffer(o.getItemCostA(), o.getItemCostB(), o.getResult(), Math.min(had.getUses(), o.getMaxUses()), o.getMaxUses(),
							o.getXp(), o.getPriceMultiplier(), o.getDemand()));
						break;
					}
				}
			}
		} else {
			v.setAttached(CHANGER_DAY, day);
		}
		v.setOffers(fresh);
	}

	/** A changer's counter in town coin {@code local}: every other realm's coin bought and sold at the going rate, less its spread. */
	static MerchantOffers changes(MinecraftServer server, Currency local) {
		MerchantOffers offers = new MerchantOffers();
		for (Currency other : Currency.values()) {
			// (0.7.484) only the realms' coins: no gold or bent copper over a town's counter
			if (other == local || !other.human()) {
				continue;
			}
			add(offers, changeOffer(server, other, local));
			add(offers, changeOffer(server, local, other));
		}
		return offers;
	}

	private static void add(MerchantOffers offers, @Nullable MerchantOffer offer) {
		if (offer != null) {
			offers.add(offer);
		}
	}

	/** {@code from} coins in, {@code to} coins out: about {@link #CHANGE_WORTH} emeralds' worth out, the changer's spread taken from what goes in. */
	private static @Nullable MerchantOffer changeOffer(MinecraftServer server, Currency from, Currency to) {
		double rateFrom = rate(server, from), rateTo = rate(server, to);
		int out = Math.max(1, (int)Math.round(CHANGE_WORTH * 1000.0 / rateTo));
		int in = (int)Math.ceil(out * rateTo / (rateFrom * (1.0 - SPREAD)));
		if (in < 1 || in > 64 || out > 64) {
			return null;
		}
		return new MerchantOffer(new ItemCost(from.item(), in), new ItemStack(to.item(), out), CHANGE_USES, 0, 0.0F);
	}

	/** Every so often a town big enough takes on a money-changer from its idle folk. */
	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() % STAFF_EVERY != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || f.holdings().population() < CHANGER_POPULATION || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			staff(level, f);
		}
	}

	private static void staff(ServerLevel level, Faction f) {
		List<Villager> about = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(STAFF_RANGE), v -> v.isAlive() && !Visitors.is(v));
		int have = 0;
		for (Villager v : about) {
			have += changer(v) ? 1 : 0;
		}
		int want = Math.min(CHANGERS_MOST, 1 + (f.holdings().population() - CHANGER_POPULATION) / CHANGER_PER);
		if (have >= want) {
			return;
		}
		for (Villager v : about) {
			if (!v.isBaby() && v.getVillagerData().profession().is(VillagerProfession.NONE) && !(v instanceof Worker w && w.role() >= 0) && !Rulers.is(v)
				&& (f.leader().isEmpty() || !f.leader().get().equals(v.getUUID())) && !McaCompat.playerFamily(v) && McaCompat.following(v) == null
				&& !v.hasAttached(Emigrants.MOVE)) {
				v.setVillagerData(v.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(Professions.CHANGER)));
				// (a little experience, so the game doesn't take the trade off them for want of a job site)
				v.setVillagerXp(Math.max(1, v.getVillagerXp()));
				v.setPersistenceRequired();
				v.setOffers(changes(level.getServer(), of(level.getServer(), f)));
				v.setAttached(CHANGER_DAY, Quests.day(level));
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2.1, v.getZ(), 5, 0.3, 0.2, 0.3, 0.0);
				return;
			}
		}
	}

	/** {@code /kingdom currency}: the coin of the settlement nearby, and what each coin fetches now (in emeralds). */
	private static int report(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		Faction f = Factions.around(server, c.getSource().getLevel(), BlockPos.containing(c.getSource().getPosition()), 160.0);
		if (f == null || f.fallen()) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.currency_none"));
			return 0;
		}
		Component rates = Component.empty();
		for (Currency x : Currency.values()) {
			if (x.human() && x != Currency.EMERALD) {
				rates = rates.copy().append(Component.literal("  ")).append(Component.translatable(x.key())).append(Component.literal(" " + rate(server, x) / 1000.0));
			}
		}
		Component shown = rates;
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.currency", f.name(), Component.translatable(of(server, f).key()), shown), false);
		return 1;
	}
}
