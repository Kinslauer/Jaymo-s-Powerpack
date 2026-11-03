package com.jaymoskingdoms;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.mojang.brigadier.context.CommandContext;
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
	/** Money-changers: a town needs this many folk to have one, and another for every this many; at most this many. */
	static final int CHANGER_POPULATION = 8, CHANGER_PER = 24, CHANGERS_MOST = 2;
	/** How often (ticks) towns are looked at for a changer, and how far from the seat one is looked for. */
	static final int STAFF_EVERY = 400;
	static final double STAFF_RANGE = Economy.POPULATION_RANGE;
	/** A kingdom's taxes: this share (percent) is also paid into the storehouse in its coin, for the town's own wages. */
	static final int TAX_COIN_PERCENT = 20;
	// ================================================================================================================

	public static Item REALM_SILVER, HACKSILVER, GOLD_DINAR, SHELL_STRING, MON_COIN;

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

	/** {@code emeralds} of worth as a count of {@code c} coins, rounded down but never nothing (what a seller is paid). */
	public static int coinsPaid(Currency c, long emeralds) {
		return (int)Math.min(Integer.MAX_VALUE, Math.max(1L, emeralds * 1000L / c.base));
	}

	/** {@code n} of {@code c}'s coins, as shown ("12 Realm Silver"). */
	public static Component amount(Currency c, long n) {
		return Component.literal(n + " ").append(new ItemStack(c.item()).getHoverName());
	}

	/** {@code n} of {@code c}'s coins as a stack of at most one stack. */
	public static ItemStack coins(Currency c, int n) {
		return new ItemStack(c.item(), Mth.clamp(n, 1, 64));
	}

	/** Gives {@code player} {@code n} of {@code c}'s coins (what doesn't fit in the pack drops at their feet). */
	public static void give(ServerPlayer player, Currency c, long n) {
		for (long left = n; left > 0L; left -= 64L) {
			player.getInventory().placeItemBackInInventory(new ItemStack(c.item(), (int)Math.min(64L, left)), net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	/** Pays {@code player} {@code emeralds} of worth in the coin of {@code f}. Returns the coins paid. */
	public static int pay(MinecraftServer server, ServerPlayer player, @Nullable Faction f, long emeralds) {
		Currency c = of(server, f);
		int n = coinsPaid(c, emeralds);
		give(player, c, n);
		return n;
	}

	/**
	 * Takes up to {@code emeralds} of worth from {@code player} as a fine: the coin of {@code f} first, then emeralds (every
	 * trader takes those). Returns the worth taken.
	 */
	public static int fine(MinecraftServer server, ServerPlayer player, @Nullable Faction f, int emeralds) {
		Currency c = of(server, f);
		long taken = 0L;
		if (c != Currency.EMERALD) {
			int coins = Math.min(Purse.count(player, c.item()), coinsFor(c, emeralds));
			if (coins > 0) {
				Purse.take(player, c.item(), coins);
				taken = coins * (long)c.base / 1000L;
			}
		}
		long rest = emeralds - taken;
		if (rest > 0L) {
			int n = (int)Math.min(rest, Purse.count(player, Items.EMERALD));
			if (n > 0) {
				Purse.take(player, Items.EMERALD, n);
				taken += n;
			}
		}
		return (int)Math.min(emeralds, taken);
	}

	/** What {@code player} carries in coin that {@code f} takes, as emeralds of worth (its coin and emeralds). */
	public static long held(MinecraftServer server, ServerPlayer player, @Nullable Faction f) {
		Currency c = of(server, f);
		long worth = Purse.count(player, Items.EMERALD);
		if (c != Currency.EMERALD) {
			worth += Purse.count(player, c.item()) * (long)c.base / 1000L;
		}
		return worth;
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
			villager.setOffers(changes(server, c));
			return;
		}
		if (Underworld.blackMarket(villager) || !c.human()) {
			return;
		}
		MerchantOffers offers = villager.getOffers();
		for (int i = 0; i < offers.size(); i++) {
			MerchantOffer o = offers.get(i);
			ItemCost a = convert(o.getItemCostA(), c);
			Optional<ItemCost> b = o.getItemCostB().map(x -> convert(x, c));
			ItemStack result = convert(o.getResult(), c);
			if (a == o.getItemCostA() && b.equals(o.getItemCostB()) && result == o.getResult()) {
				continue;
			}
			MerchantOffer fresh = new MerchantOffer(a, b, result, o.getUses(), o.getMaxUses(), o.getXp(), o.getPriceMultiplier(), o.getDemand());
			int was = o.getItemCostA().count();
			fresh.setSpecialPriceDiff(was <= 0 ? 0 : Math.round(o.getSpecialPriceDiff() * (float)a.count() / was));
			offers.set(i, fresh);
		}
	}

	/** {@code cost} asked in {@code c}'s coin instead (the same worth, rounded up), or {@code cost} itself if it is not money or can't be. */
	private static ItemCost convert(ItemCost cost, Currency c) {
		Currency from = Currency.byItem(cost.item().value());
		if (from == null || !from.human() || from == c) {
			return cost;
		}
		long n = (cost.count() * (long)from.base + c.base - 1L) / c.base;
		return n < 1L || n > 64L ? cost : new ItemCost(c.item(), (int)n);
	}

	/** {@code stack} paid out in {@code c}'s coin instead (the same worth, rounded down but never nothing), else {@code stack} itself. */
	private static ItemStack convert(ItemStack stack, Currency c) {
		Currency from = stack.isEmpty() ? null : Currency.byItem(stack.getItem());
		if (from == null || !from.human() || from == c) {
			return stack;
		}
		long n = Math.max(1L, stack.getCount() * (long)from.base / c.base);
		return n > 64L ? stack : new ItemStack(c.item(), (int)n);
	}

	// ---- money-changers --------------------------------------------------------------------------------------------------------

	public static boolean changer(Villager v) {
		return v.getVillagerData().profession().value() == Professions.CHANGER;
	}

	/** A changer's counter in town coin {@code local}: every other coin bought and sold at the going rate, less its spread. */
	static MerchantOffers changes(MinecraftServer server, Currency local) {
		MerchantOffers offers = new MerchantOffers();
		for (Currency other : Currency.values()) {
			if (other == local) {
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
