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
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import com.jaymoskingdoms.money.Money;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Currencies, Andy (2026-11-03): "Diversify the currencies, it can change based on the prominent trading hub's
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
 * (0.7.498) Coins replace emeralds, Andy (2026-11-04): "Combine: culture coins replace emeralds". Change is made in the
 * copper bit ({@link Currency#BIT}) and the emerald is a gem the changer buys and sells ({@link #EMERALD_BUY}); only the
 * keepers ({@link #keeper}), who "will still trade in emeralds", price in it. The purse, the charge and the payout are
 * {@link Money}'s; what is left here is which coin, what the sums come to, and the prices.
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
	 * (0.7.490) Coin is only kept in a storehouse up to this much worth (emeralds), the town's wage reserve: past it, the
	 * coined share of the taxes stays in the treasury, so coin doesn't pile up and crowd the storehouse.
	 */
	static final int TAX_COIN_RESERVE = 48;
	/** (0.7.490) A payout or price split into coin and emerald change may be off its worth by this share at most, to use more of the town's coin. */
	static final double PAY_SLACK = 0.01;
	/** (0.7.490) How many coin counts below the most are tried when a sum is split into coin and emerald change. */
	static final int PAY_TRIES = 12;
	/**
	 * (0.7.490) A price is put in a coin only when that many coins come within this share of its worth (asked: never under
	 * it; paid: never over it); else it stays in emeralds, or its own coin, so rounding to a big coin never makes money.
	 */
	static final double ROUND_SLACK = 0.10;
	/** (0.7.490) How many offers' worths a trader keeps, those in its list now first, then those put away for a while (TierGate). */
	static final int BASES_KEPT = 64;
	/**
	 * (0.7.498) A payer takes a coin of another realm than the settlement's at its going rate less this share
	 * ({@link #FOREIGN_TAKEN} off: never). The settlement's own coin and the bit are taken at par.
	 */
	public static final double FOREIGN_CUT = 0.10;
	public static final boolean FOREIGN_TAKEN = true;
	/** (0.7.498) A money-changer buys an emerald (a gem now) at this share of its worth, and sells one at this much of it. */
	public static final double EMERALD_BUY = 0.90, EMERALD_SELL = 1.12;
	/** (0.7.498) A diamond is worth this many emeralds to a changer, who buys it but doesn't sell it. */
	public static final int DIAMOND_WORTH = 4;
	/** (0.7.498) A price over a stack of the town's coin is asked in this coin (more by worth) in the first slot, the rest in the town's coin. */
	public static final Currency BIG_COIN = Currency.DINAR;
	/** (0.7.498) One bit's worth, in thousandths of an emerald (the change every sum is made up with). */
	private static final long BIT_BASE = Currency.BIT.base;
	/** (0.7.498) The share of villagers, and of wandering traders, who keep their emerald prices (Andy: "some you come across will still trade in emeralds"). Fixed by who they are, so it never changes. */
	static final float KEEPER_VILLAGERS = 0.15F, KEEPER_WANDERERS = 0.4F;
	/** (0.7.498) A changer's gem lot may be this much worse than the best lot of up to a stack, so the lots stay small. */
	static final double LOT_SLACK = 0.02;
	/** (0.7.498) How far (blocks) a trader outside any town looks for a settlement whose coin it uses ({@link #at}). */
	static final double AT_RANGE = 160.0;
	// ================================================================================================================

	public static Item REALM_SILVER, HACKSILVER, GOLD_DINAR, SHELL_STRING, MON_COIN;
	/** (0.7.498) The copper bit's item ({@code jaymoskingdoms:copper_coin}, registered with the money items), found when first asked for. */
	private static Item copperBit;

	/**
	 * (0.7.490) A trader's offer as it was before any town's coin was put on it: its place in the trader's list when last
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

	/** (0.7.490) A trader's offers in their own coin ({@link Base}), the ones with coin in them. */
	static final AttachmentType<List<Base>> BASES = AttachmentRegistry.create(JaymosKingdoms.id("trade_bases"),
		b -> b.persistent(Base.CODEC.listOf()));
	/** (0.7.490) The day a money-changer's counter last restocked. */
	static final AttachmentType<Long> CHANGER_DAY = AttachmentRegistry.create(JaymosKingdoms.id("changer_day"), b -> b.persistent(Codec.LONG));

	/** (0.7.498) Faction id → (its coin, when it was worked out, the id of the trading hub whose coin it took or -1). */
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

	/** (0.7.498) The copper bit's item; air until the money items are registered (nothing is a coin then). */
	static Item bit() {
		if (copperBit == null) {
			Identifier id = JaymosKingdoms.id("copper_coin");
			if (!BuiltInRegistries.ITEM.containsKey(id)) {
				return Items.AIR;
			}
			copperBit = BuiltInRegistries.ITEM.getValue(id);
		}
		return copperBit;
	}

	private static Item item(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new HavenWares.Ware(new Item.Properties().setId(key), name));
	}

	// ---- which coin ----------------------------------------------------------------------------------------------------

	/**
	 * The coin {@code f} trades in (the realm silver for none: (0.7.498) the null callers are a bounty court or a quarrel
	 * whose faction is gone, and the like, all of which want a coin a purse holds). A barbarian camp that is neither
	 * greenskin nor pirate still answers emeralds, only to seed its hoard; what is paid to or by it falls back to silver
	 * ({@link Money}). Worked out afresh every {@link #RESOLVE_TTL} ticks.
	 */
	public static Currency of(MinecraftServer server, @Nullable Faction f) {
		if (f == null) {
			return Currency.SILVER;
		}
		return known(server, f)[0] instanceof Currency c ? c : Currency.SILVER;
	}

	/** (0.7.498) The resolved entry of {@code f}: its coin, the time, and the id of the trading hub it took the coin of (-1 for none). */
	private static Object[] known(MinecraftServer server, Faction f) {
		long now = Factions.level(server).getGameTime();
		Object[] known = RESOLVED.get(f.id());
		if (known != null && now - (Long)known[1] < RESOLVE_TTL) {
			return known;
		}
		Object[] fresh = resolve(server, f);
		known = new Object[] {fresh[0], now, fresh[1]};
		RESOLVED.put(f.id(), known);
		return known;
	}

	/**
	 * (0.7.498) The name of the trading hub whose coin {@code f} uses, if resolving chose one (a much richer kingdom nearby);
	 * null where it uses the coin of its own land (or its overlord's, or a camp's).
	 */
	public static @Nullable String hubName(MinecraftServer server, @Nullable Faction f) {
		if (f == null) {
			return null;
		}
		Object[] known = known(server, f);
		int hub = known[2] instanceof Integer id ? id : -1;
		Faction k = hub < 0 ? null : Factions.get(server, hub);
		return k == null || k.fallen() ? null : k.name();
	}

	/**
	 * (0.7.498) The coin of a trader that stands outside any settlement of its own (a wanderer, a wild village's folk, a
	 * Kitchen shop in the wilds): the standing settlement within {@link #AT_RANGE} blocks of {@code pos}, if there is one
	 * and it is no camp, else the realm silver.
	 */
	public static Currency at(Level level, BlockPos pos) {
		if (!(level instanceof ServerLevel sl) || sl.getServer() == null) {
			return Currency.SILVER;
		}
		MinecraftServer server = sl.getServer();
		Faction f = Factions.around(server, sl, pos, AT_RANGE);
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
			return Currency.SILVER;
		}
		return of(server, f);
	}

	/** The coin and the hub's id ({@code -1} for none). */
	private static Object[] resolve(MinecraftServer server, Faction f) {
		if (f.type() == FactionType.BARBARIAN) {
			return new Object[] {Hordes.greenskin(server, f.id()) ? Currency.BENT : PirateCoves.is(server, f.id()) ? Currency.GOLD : Currency.EMERALD, -1};
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
			return new Object[] {culture(server, hub), hub.id()};
		}
		return new Object[] {own, -1};
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
		// (0.7.498) the emerald and the bit have no realm behind them: always their standard
		if (!c.coin()) {
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
			// (0.7.498) the realms' coins only: the bit is not one of them
			for (Currency x : Currency.values()) {
				coins += x.coin() ? 1 : 0;
			}
			double mean = total / (double)coins;
			for (Currency x : Currency.values()) {
				if (!x.coin()) {
					continue;
				}
				double ratio = mean <= 0.0 ? 1.0 : wealth.getOrDefault(x, 0L) / mean;
				FACTORS.put(x, Mth.clamp(RATE_FLOOR + RATE_SWING * ratio, RATE_FLOOR, RATE_CEIL));
			}
		}
		return Math.max(1, (int)Math.round(c.base * FACTORS.getOrDefault(c, 1.0)));
	}

	// ---- amounts -----------------------------------------------------------------------------------------------------------

	/** {@code stack} is one of the coins a human trader's prices name (an emerald, a realm's coin or the bit). */
	public static boolean isMoney(ItemStack stack) {
		Currency c = stack.isEmpty() ? null : Currency.byItem(stack.getItem());
		return c != null && c.human();
	}

	/** {@code emeralds} of worth as a count of {@code c} coins, rounded up (what a buyer is asked for). */
	public static int coinsFor(Currency c, long emeralds) {
		return (int)Math.min(Integer.MAX_VALUE, Math.max(1L, (emeralds * 1000L + c.base - 1L) / c.base));
	}

	/**
	 * (0.7.490) A sum as it is handed over: whole coins of {@code coin}, and the change in emeralds. What is paid out
	 * ({@link #pay}) is never worth more than is owed, and what is asked ({@link #owe}) never less, so no rounding to a big
	 * coin makes money. (0.7.498) The change is in copper bits ({@code bits}), not emeralds.
	 */
	public record Pay(Currency coin, long coins, long bits) {
		/** What it is worth, in thousandths of an emerald. */
		public long milli() {
			return this.coins * this.coin.base + this.bits * Currency.BIT.base;
		}

		public boolean isEmpty() {
			return this.coins <= 0L && this.bits <= 0L;
		}

		/** As stacks: the coin first, then the bits. */
		public List<ItemStack> stacks() {
			List<ItemStack> out = new ArrayList<>();
			for (long left = this.coins; left > 0L; left -= 64L) {
				out.add(new ItemStack(this.coin.item(), (int)Math.min(64L, left)));
			}
			for (long left = this.bits; left > 0L; left -= 64L) {
				out.add(new ItemStack(Currency.BIT.item(), (int)Math.min(64L, left)));
			}
			return out;
		}
	}

	/** (0.7.490) {@code milli} of worth paid out in {@code c}: as many whole coins as fit, the change in bits, never more than the worth. */
	public static Pay pay(Currency c, long milli) {
		return split(c, milli, false, Long.MAX_VALUE);
	}

	/** (0.7.490) {@code emeralds} of worth paid out in {@code c} ({@link #pay}). */
	public static Pay payEmeralds(Currency c, long emeralds) {
		return pay(c, emeralds * 1000L);
	}

	/** (0.7.490) {@code milli} of worth asked in {@code c}: whole coins, the rest in bits, never less than the worth. */
	public static Pay owe(Currency c, long milli) {
		return split(c, milli, true, Long.MAX_VALUE);
	}

	/**
	 * (0.7.490) {@code milli} split into at most {@code most} of {@code c}'s coins and change in bits, over the worth if
	 * {@code over} (asked), else under it (paid): the exact split with the most coins, or if there is none, of those
	 * within {@link #PAY_SLACK} of the worth (or as near as any comes), the one with the most coins.
	 */
	static Pay split(Currency c, long milli, boolean over, long most) {
		if (milli <= 0L) {
			return new Pay(c, 0L, 0L);
		}
		// (0.7.498) the emerald (a keeper's coin) and the bit are paid in whole coins, with no change
		if (c == Currency.EMERALD || c == Currency.BIT) {
			return new Pay(c, over ? (milli + c.base - 1L) / c.base : milli / c.base, 0L);
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
		return new Pay(c, k, over ? (rest + BIT_BASE - 1L) / BIT_BASE : rest / BIT_BASE);
	}

	private static long splitError(Currency c, long milli, boolean over, long k) {
		long rest = Math.max(0L, milli - k * c.base);
		long e = over ? (rest + BIT_BASE - 1L) / BIT_BASE : rest / BIT_BASE;
		return Math.abs(k * c.base + e * BIT_BASE - milli);
	}

	/** {@code n} of {@code c}'s coins, as shown ("12 Realm Silver"). */
	public static Component amount(Currency c, long n) {
		return Component.literal(n + " ").append(new ItemStack(c.item()).getHoverName());
	}

	/** (0.7.490) {@code p} as shown: "12 Realm Silver", or with its change, "1 Gold Dinar, 3 Copper Bit". */
	public static Component amount(Pay p) {
		if (p.coins() <= 0L && p.bits() > 0L) {
			return amount(Currency.BIT, p.bits());
		}
		if (p.bits() <= 0L) {
			return amount(p.coin(), p.coins());
		}
		return amount(p.coin(), p.coins()).copy().append(Component.literal(", ")).append(amount(Currency.BIT, p.bits()));
	}

	/** (0.7.490) Gives {@code player} {@code p}: its coins and its change ((0.7.498) into the worn purse first, then the pack, then the ground: {@link Money#deposit}). */
	public static void give(ServerPlayer player, Pay p) {
		for (ItemStack stack : p.stacks()) {
			Money.deposit(player, stack);
		}
	}

	/**
	 * (0.7.490) The worth (thousandths of an emerald) {@code player} carries that {@code f} takes. (0.7.498) Counted by
	 * {@link Money#count(ServerPlayer, Faction)}: its coin and the bits at par, other realms' coins less {@link #FOREIGN_CUT},
	 * in the pack, the purses and the bundles; never emeralds.
	 */
	public static long purse(MinecraftServer server, ServerPlayer player, @Nullable Faction f) {
		return Money.count(player, f);
	}

	/** (0.7.490) {@code player} carries {@code emeralds} of worth that {@code f} takes ({@link #purse}). */
	public static boolean canPay(MinecraftServer server, ServerPlayer player, @Nullable Faction f, long emeralds) {
		return Money.canPay(player, f, emeralds * 1000L);
	}

	/** (0.7.490) The worth (thousandths of an emerald) of the realms' coin in {@code f}'s storehouse (not its emeralds, which are goods as much as coin, nor the bits). */
	static long stored(ServerLevel level, Faction f) {
		long milli = 0L;
		for (Currency x : Currency.values()) {
			if (x.coin()) {
				milli += Storage.count(level, f, x.item()) * (long)x.base;
			}
		}
		return milli;
	}

	/** (0.7.490) {@code emeralds} of worth as {@code f} asks it ({@link #owe}), as shown. */
	public static Component asked(MinecraftServer server, @Nullable Faction f, long emeralds) {
		return amount(owe(of(server, f), emeralds * 1000L));
	}

	/** Gives {@code player} {@code n} of {@code c}'s coins ((0.7.498) as {@link #give(ServerPlayer, Pay)}: purse first; what doesn't fit drops at their feet). */
	public static void give(ServerPlayer player, Currency c, long n) {
		for (long left = n; left > 0L; left -= 64L) {
			Money.deposit(player, new ItemStack(c.item(), (int)Math.min(64L, left)));
		}
	}

	/**
	 * Takes up to {@code emeralds} of worth from {@code player} as a fine, in what {@code f} takes ({@link Money#chargeUpTo}:
	 * (0.7.498) its coin, bits and other realms' coins, from the pack, the purses and the bundles; never emeralds). Returns the
	 * worth taken, to the nearest emerald (what the callers count into a treasury).
	 */
	public static int fine(MinecraftServer server, ServerPlayer player, @Nullable Faction f, int emeralds) {
		long taken = Money.chargeUpTo(player, f, emeralds * 1000L);
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
	 * (from {@code VillagerPriceMixin}, last, when {@code player} opens a trade with {@code villager}; for a wandering
	 * trader, which has no special prices to update, from {@code VillagerCoinMixin}'s trading-player hook) a trader's
	 * emerald (or other realm's coin) prices are set in the coin of the town it stands in; a money-changer's offers are
	 * made afresh at the going rate. (0.7.498) Any trader: a keeper ({@link #keeper}) is priced in emeralds, one outside a
	 * town in the coin of the settlement round it or the realm silver ({@link #at}).
	 */
	public static void localize(AbstractVillager villager, Player player) {
		if (!(villager.level() instanceof ServerLevel level) || villager.isBaby()) {
			return;
		}
		Faction f = Quests.settlement(level, villager);
		MinecraftServer server = level.getServer();
		Currency c = keeper(villager) ? Currency.EMERALD : f != null ? of(server, f) : at(level, villager.blockPosition());
		if (villager instanceof Villager v && changer(v)) {
			counter(level, v, c);
			return;
		}
		// (0.7.498) a den's fence is priced in coin too now: emeralds are a gem, and only the keepers deal in them
		if (!c.human()) {
			return;
		}
		// (0.7.490) each price worked out from the offer's own worth (Base), never from the coin it was last set in; a Base is
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
			// (0.7.490) the goods as they first were, before any scaling to a town's coin
			ItemCost goodsA = was.a() > 0 ? o.getItemCostA() : recount(o.getItemCostA(), was.ga());
			Optional<ItemCost> goodsB = was.b() > 0 ? o.getItemCostB() : was.gb() == 0 ? Optional.empty() : o.getItemCostB().map(x -> recount(x, was.gb()));
			ItemStack goodsR = was.r() > 0 ? o.getResult() : recount(o.getResult(), was.gr());
			// (0.7.498) whether the price lands near its worth in a coin of the chain; if not, it is still put in coin, by the
			// least overshoot (asked), unless the single-slot rescues below find a better way
			ItemCost fitA = was.a() > 0 ? fitAsked(was.a(), c, was.ca()) : null;
			ItemStack fitR = was.r() > 0 ? fitPaid(was.r(), c, was.cr()) : null;
			ItemCost a = was.a() > 0 ? asked(was.a(), c, was.ca(), o.getItemCostA()) : goodsA;
			Optional<ItemCost> b = was.b() > 0 ? o.getItemCostB().map(x -> asked(was.b(), c, was.cb(), x)) : goodsB;
			ItemStack result = was.r() > 0 ? paid(was.r(), c, was.cr(), o.getResult()) : goodsR;
			// (0.7.490) a price that won't land on whole town coins: the goods are scaled until it does (2 hacksilver for 16
			// wheat, not 1 emerald for 20), or else the rest is asked as copper bits in the empty second slot
			boolean single = was.b() == 0 && was.gb() == 0;
			if (single && was.a() > 0 && was.r() == 0 && (fitA == null || fitA.item().value() != c.item()) && !goodsR.isEmpty()) {
				int[] scaled = scale(was.a(), c, goodsR.getCount(), goodsR.getMaxStackSize(), true);
				int[] big = scaled == null ? big(c, was.a()) : null;
				if (scaled != null) {
					a = new ItemCost(c.item(), scaled[0]);
					result = goodsR.copyWithCount(scaled[1]);
				} else if (big != null) {
					// (0.7.498) over a stack of the town's coin: the bigger coin, and the rest in the town's
					a = new ItemCost(BIG_COIN.item(), big[0]);
					b = big[1] > 0 ? Optional.of(new ItemCost(c.item(), big[1])) : Optional.empty();
				} else {
					// (0.7.498) the rest in copper bits, where the town's coin does not land on the price
					Pay owed = split(c, was.a(), true, 64L);
					if (owed.coins() >= 1L && owed.bits() >= 1L && owed.bits() <= 64L && near(owed.milli(), was.a(), true)) {
						a = new ItemCost(c.item(), (int)owed.coins());
						b = Optional.of(new ItemCost(Currency.BIT.item(), (int)owed.bits()));
					}
				}
			} else if (single && was.r() > 0 && was.a() == 0 && (fitR == null || !fitR.is(c.item()))) {
				int[] scaled = scale(was.r(), c, goodsA.count(), goodsA.itemStack().getMaxStackSize(), false);
				if (scaled != null) {
					result = new ItemStack(c.item(), scaled[0]);
					a = recount(goodsA, scaled[1]);
				}
			}
			String print = print(a, b, result);
			now.add(new Base(i, print, was.a(), was.b(), was.r(), was.ca(), was.cb(), was.cr(), was.ga(), was.gb(), was.gr()));
			// (0.7.490) vanilla's discount (and Purists' and Prejudice's) is a count of whatever cost A is now, set afresh on
			// every open: it is put back in the goods or coin it was first asked in, then into cost A as it now is, every time
			int diff = discount(o, was, a);
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
	 * (0.7.490) {@code offer}'s discount (or mark-up) counted as though cost A were still as it was first asked ({@code was}),
	 * as a count of {@code a}: coin by worth (each of the coin it was first asked in), goods by count. Where the first count
	 * isn't known, cost A as it is stands for it. (0.7.498) A discount never takes the price under the worth of one of what
	 * it was first asked in (vanilla's floor of one emerald), or under one coin: a count of 1 emerald is not made 1 bit.
	 */
	private static int discount(MerchantOffer offer, Base was, ItemCost a) {
		int diff = offer.getSpecialPriceDiff();
		if (diff == 0) {
			return 0;
		}
		Currency to = Currency.byItem(a.item().value());
		if (was.a() > 0 && to != null && to.human()) {
			Currency from = byId(was.ca());
			float each = from != null && from.human() ? from.base : was.ga() > 0 ? (float)was.a() / was.ga() : 0.0F;
			if (each <= 0.0F) {
				return diff;
			}
			int scaled = Math.round(diff * each / to.base);
			if (scaled < 0) {
				// the least the price may come to, in counts of a: what vanilla would clamp it at, with demand's share of it
				int demand = Math.max(0, Mth.floor(a.count() * offer.getDemand() * offer.getPriceMultiplier()));
				int least = Math.max(1, (int)Math.ceil(each / to.base));
				scaled = Math.max(scaled, Math.min(0, least - a.count() - demand));
			}
			return scaled;
		}
		int first = was.ga() > 0 ? was.ga() : offer.getItemCostA().count();
		return first <= 0 ? diff : Math.round(diff * (float)a.count() / first);
	}

	/** (0.7.490) {@code cost} with {@code n} of its goods ({@code n} of 0 or less: as it is). */
	private static ItemCost recount(ItemCost cost, int n) {
		return n <= 0 || n == cost.count() ? cost : new ItemCost(cost.item(), n, cost.components());
	}

	private static ItemStack recount(ItemStack stack, int n) {
		return n <= 0 || n == stack.getCount() || stack.isEmpty() ? stack : stack.copyWithCount(n);
	}

	/**
	 * (0.7.490) {@code goods} goods worth {@code milli} traded instead as {coins, goods} of {@code c}'s coin, the price per
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

	/**
	 * (0.7.498) {@code milli} asked over a stack of {@code c}'s coins: {bigger coins, c's coins for the rest} with the
	 * bigger coin ({@link #BIG_COIN}) in the first slot, never under the worth and within {@link #ROUND_SLACK} of it. Null
	 * where the price fits a stack of {@code c} (or the bigger coin is {@code c}, or more than a stack of it).
	 */
	static int @Nullable [] big(Currency c, long milli) {
		if (!c.coin() || c == BIG_COIN || milli <= 0L || (milli + c.base - 1L) / c.base <= 64L) {
			return null;
		}
		long k = milli / BIG_COIN.base;
		long n = (milli - k * BIG_COIN.base + c.base - 1L) / c.base;
		if (k < 1L || k > 64L || n > 64L || !near(k * BIG_COIN.base + n * c.base, milli, true)) {
			return null;
		}
		return new int[] {(int)k, (int)n};
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

	/** (0.7.490) {@code coins} (thousandths of an emerald) is within {@link #ROUND_SLACK} of {@code milli}: over it if {@code over}, else under it. */
	public static boolean near(long coins, long milli, boolean over) {
		return over ? coins >= milli && coins <= milli * (1.0 + ROUND_SLACK) : coins <= milli && coins >= milli * (1.0 - ROUND_SLACK);
	}

	/**
	 * (0.7.498) The coins a price is tried in, in order: the town's coin, the copper bit, the coin it was {@code first}
	 * asked in, the bigger coin. Never the emerald unless it is the town's coin (a keeper's).
	 */
	private static Currency[] chain(Currency c, String first) {
		List<Currency> out = new ArrayList<>();
		for (Currency x : new Currency[] {c, Currency.BIT, byId(first), BIG_COIN}) {
			if (x != null && x.human() && (x != Currency.EMERALD || c == Currency.EMERALD) && !out.contains(x)) {
				out.add(x);
			}
		}
		return out.toArray(new Currency[0]);
	}

	/**
	 * {@code milli} of worth asked in {@code c}'s coin (rounded up); else in the copper bit; else in the coin {@code first} it
	 * was first asked in; else the bigger coin; else {@code fallback}. A coin is used only for a stack or less, and when it
	 * comes near the worth.
	 */
	private static ItemCost asked(int milli, Currency c, String first, ItemCost fallback) {
		ItemCost fit = fitAsked(milli, c, first);
		if (fit != null) {
			return fit;
		}
		// (0.7.498) no coin lands within the slack, and a trader who deals in coin never asks emeralds (a book's trade has
		// goods in slot B, so there is no room for change): the coin that goes over the worth by the least, else the biggest stack
		if (c != Currency.EMERALD) {
			Currency pick = null;
			long least = Long.MAX_VALUE;
			Currency biggest = null;
			for (Currency x : chain(c, first)) {
				long n = (milli + x.base - 1L) / x.base;
				if (n >= 1L && n <= 64L && n * x.base - milli < least) {
					least = n * x.base - milli;
					pick = x;
				}
				if (biggest == null || x.base > biggest.base) {
					biggest = x;
				}
			}
			if (pick != null) {
				return new ItemCost(pick.item(), (int)((milli + pick.base - 1L) / pick.base));
			}
			if (biggest != null) {
				return new ItemCost(biggest.item(), 64);
			}
		}
		return fallback;
	}

	/** (0.7.498) {@code milli} asked in the first coin of the chain that comes near the worth, else null. */
	private static @Nullable ItemCost fitAsked(int milli, Currency c, String first) {
		for (Currency x : chain(c, first)) {
			long n = (milli + x.base - 1L) / x.base;
			if (n >= 1L && n <= 64L && near(n * x.base, milli, true)) {
				return new ItemCost(x.item(), (int)n);
			}
		}
		return null;
	}

	/** (0.7.498) {@code milli} paid in the first coin of the chain that comes near the worth, else null. */
	private static @Nullable ItemStack fitPaid(int milli, Currency c, String first) {
		for (Currency x : chain(c, first)) {
			long n = milli / x.base;
			if (n >= 1L && n <= 64L && near(n * x.base, milli, false)) {
				return new ItemStack(x.item(), (int)n);
			}
		}
		return null;
	}

	/** {@code milli} of worth paid in {@code c}'s coin (rounded down), else in bits, else in its first coin, else the bigger coin, as for {@link #asked}; else {@code fallback}. */
	private static ItemStack paid(int milli, Currency c, String first, ItemStack fallback) {
		ItemStack fit = fitPaid(milli, c, first);
		if (fit != null) {
			return fit;
		}
		// (0.7.498) as for asked: a trader who deals in coin never pays emeralds, so the coin that comes under the worth by
		// the least (a bit at the least)
		if (c != Currency.EMERALD) {
			Currency pick = null;
			long least = Long.MAX_VALUE;
			Currency smallest = null;
			for (Currency x : chain(c, first)) {
				long n = milli / x.base;
				if (n >= 1L && n <= 64L && milli - n * x.base < least) {
					least = milli - n * x.base;
					pick = x;
				}
				if (smallest == null || x.base < smallest.base) {
					smallest = x;
				}
			}
			if (pick != null) {
				return new ItemStack(pick.item(), (int)(milli / pick.base));
			}
			if (smallest != null) {
				return new ItemStack(smallest.item(), 1);
			}
		}
		return fallback;
	}

	// ---- money-changers --------------------------------------------------------------------------------------------------------

	public static boolean changer(Villager v) {
		return v.getVillagerData().profession().value() == Professions.CHANGER;
	}

	/**
	 * (0.7.498) {@code e} is a keeper, one of the traders who "will still trade in emeralds" (Andy): a grown villager or
	 * wandering trader with a trade of their own, picked by a lot drawn from their UUID ({@link #KEEPER_VILLAGERS} of
	 * villagers, {@link #KEEPER_WANDERERS} of wanderers), so it never changes. Not a money-changer, and not one whose wares
	 * are always priced in coin: no jobless or nitwit villager, none MCA keeps from trading (guards, archers, outlaws,
	 * mercenaries, our workers), no pedlar, no Ignis Vael.
	 */
	public static boolean keeper(@Nullable Entity e) {
		return e instanceof AbstractVillager t && !t.isBaby() && trades(t) && !(t instanceof Villager v && changer(v)) && lot(t);
	}

	/** (0.7.498) {@code t} has a trade to deal in (a wandering trader, or a villager with a job that trades). */
	static boolean trades(AbstractVillager t) {
		if (t instanceof WanderingTrader) {
			return true;
		}
		return t instanceof Villager v && !v.getVillagerData().profession().is(VillagerProfession.NONE)
			&& !v.getVillagerData().profession().is(VillagerProfession.NITWIT) && !(McaCompat.LOADED && McaBridge.cannotTrade(v))
			&& !Pedlars.pedlar(v) && !com.jaymoskingdoms.nether.CinderForge.ignis(v);
	}

	/** (0.7.498) {@code t} drew the keepers' lot, by its UUID (the draw {@code Coinage.keepsEmeralds} made, kept for the saves that have been through it). */
	private static boolean lot(AbstractVillager t) {
		float share = t instanceof WanderingTrader ? KEEPER_WANDERERS : KEEPER_VILLAGERS;
		long bits = t.getUUID().getLeastSignificantBits() ^ 0x5DEECE66DL;
		return Math.floorMod(bits * 0x9E3779B97F4A7C15L >>> 40, 10000L) < share * 10000.0F;
	}

	/**
	 * (0.7.490) A changer's counter set at today's rates in town coin {@code local}, each trade keeping the uses it has had
	 * since it last restocked (once every {@link #CHANGE_RESTOCK_DAYS}), so opening the trade again doesn't restock it.
	 */
	private static void counter(ServerLevel level, Villager v, Currency local) {
		// (0.7.498) a realm's coin only: a camp's, or a keeper's emeralds, has no counter
		if (!local.coin()) {
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

	/**
	 * A changer's counter in town coin {@code local}: every other realm's coin bought and sold at the going rate, less its
	 * spread. (0.7.498) And the copper bit for the town's coin at par, and the gems: emeralds ({@link #EMERALD_BUY},
	 * {@link #EMERALD_SELL}), emerald blocks (nine emeralds), diamonds ({@link #DIAMOND_WORTH}, bought only).
	 */
	static MerchantOffers changes(MinecraftServer server, Currency local) {
		MerchantOffers offers = new MerchantOffers();
		for (Currency other : Currency.values()) {
			// (0.7.490) only the realms' coins: no gold or bent copper over a town's counter (0.7.498) nor the bit or the emerald, which have rows of their own
			if (other == local || !other.coin()) {
				continue;
			}
			add(offers, changeOffer(server, other, local));
			add(offers, changeOffer(server, local, other));
		}
		// the bit, at par, in lots of CHANGE_WORTH emeralds' worth
		long bits = CHANGE_WORTH * 1000L / Currency.BIT.base;
		if (bits * Currency.BIT.base % local.base == 0L) {
			int coins = (int)(bits * Currency.BIT.base / local.base);
			if (coins >= 1 && coins <= 64 && bits <= 64L) {
				add(offers, new MerchantOffer(new ItemCost(local.item(), coins), new ItemStack(Currency.BIT.item(), (int)bits), CHANGE_USES, 0, 0.0F));
				add(offers, new MerchantOffer(new ItemCost(Currency.BIT.item(), (int)bits), new ItemStack(local.item(), coins), CHANGE_USES, 0, 0.0F));
			}
		}
		// the gems
		add(offers, buys(local, Items.EMERALD, 1000));
		add(offers, sells(local, Items.EMERALD, 1000));
		add(offers, buys(local, Items.EMERALD_BLOCK, 9000));
		add(offers, sells(local, Items.EMERALD_BLOCK, 9000));
		add(offers, buys(local, Items.DIAMOND, DIAMOND_WORTH * 1000));
		return offers;
	}

	/**
	 * (0.7.498) {@code gem}s (each worth {@code unit}, in thousandths of an emerald) in, {@code local} coins out at
	 * {@link #EMERALD_BUY}: the smallest lot (at most a stack of each) that comes within {@link #LOT_SLACK} of the best
	 * share of the worth the coins can make, so the whole coins lose little to rounding.
	 */
	private static @Nullable MerchantOffer buys(Currency local, Item gem, int unit) {
		double best = -1.0;
		for (int in = 1; in <= 64; in++) {
			int out = buyOut(local, in, unit);
			if (out >= 1 && out <= 64) {
				best = Math.max(best, out * local.base / (double)(in * unit));
			}
		}
		for (int in = 1; in <= 64; in++) {
			int out = buyOut(local, in, unit);
			if (out >= 1 && out <= 64 && out * local.base / (double)(in * unit) >= best - LOT_SLACK) {
				return new MerchantOffer(new ItemCost(gem, in), new ItemStack(local.item(), out), CHANGE_USES, 0, 0.0F);
			}
		}
		return null;
	}

	/** (0.7.498) How many of {@code local}'s coins {@code in} gems of {@code unit} worth fetch at {@link #EMERALD_BUY}, rounded down. */
	private static int buyOut(Currency local, int in, int unit) {
		return (int)Math.min(1000L, (long)Math.floor(in * (double)unit * EMERALD_BUY / local.base + 1.0E-9));
	}

	/**
	 * (0.7.498) {@code local} coins in, {@code gem}s out at {@link #EMERALD_SELL}: the smallest lot (at most a stack of each)
	 * that comes within {@link #LOT_SLACK} of the cheapest the whole coins can make it.
	 */
	private static @Nullable MerchantOffer sells(Currency local, Item gem, int unit) {
		double best = Double.MAX_VALUE;
		for (int out = 1; out <= 64; out++) {
			int in = sellIn(local, out, unit);
			if (in >= 1 && in <= 64) {
				best = Math.min(best, in * (double)local.base / (out * unit));
			}
		}
		for (int out = 1; out <= 64; out++) {
			int in = sellIn(local, out, unit);
			if (in >= 1 && in <= 64 && in * (double)local.base / (out * unit) <= best + LOT_SLACK) {
				return new MerchantOffer(new ItemCost(local.item(), in), new ItemStack(gem, out), CHANGE_USES, 0, 0.0F);
			}
		}
		return null;
	}

	/** (0.7.498) How many of {@code local}'s coins {@code out} gems of {@code unit} worth cost at {@link #EMERALD_SELL}, rounded up. */
	private static int sellIn(Currency local, int out, int unit) {
		return (int)Math.min(1000L, (long)Math.ceil(out * (double)unit * EMERALD_SELL / local.base - 1.0E-9));
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
			// (0.7.498) never one of the keepers' lot, who stay emerald traders if they ever take a trade
			if (!v.isBaby() && !lot(v) && v.getVillagerData().profession().is(VillagerProfession.NONE) && !(v instanceof Worker w && w.role() >= 0) && !Rulers.is(v)
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
			if (x.coin()) {
				rates = rates.copy().append(Component.literal("  ")).append(Component.translatable(x.key())).append(Component.literal(" " + rate(server, x) / 1000.0));
			}
		}
		Component shown = rates;
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.currency", f.name(), Component.translatable(of(server, f).key()), shown), false);
		return 1;
	}
}
