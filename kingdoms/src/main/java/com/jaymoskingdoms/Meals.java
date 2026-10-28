package com.jaymoskingdoms;

import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.299) Andy (2026-10-28): "the villagers should all either cook or have somewhere to buy food, further influencing
 * the economy." Chef's Delight's cooks and chefs, remade (it needs the real Farmer's Delight, and has no 26.3 build).
 * <p>
 * Twice a day, at lunch and at supper, every one of a settlement's townsfolk (its villagers, children too; not its
 * ruler, who dines at court, nor its folk on duty) has a meal, one of two ways:
 * <ul>
 * <li><b>at home</b>, if there's a stove by their bed (a furnace, smoker, lit campfire, or Kitchen's hot blocks): they
 * take a share of raw food from the storehouse ({@link #HOME_PORTIONS} meals an item), cook it there (smoke, the
 * crackle) and eat;</li>
 * <li><b>bought in town</b> otherwise: at the {@link Buildings#cookhouse cookhouse} or the tavern, else from the stalls
 * by the bell. A meal comes out of the eatery's pot, and the pot from the storehouse: a cook's dish feeds more than raw
 * food ({@link #servings}). Every meal sold pays the realm a share in tax; a {@link Visitors visitor}'s pays it all,
 * money from outside.</li>
 * </ul>
 * The <b>price of a meal</b> follows the larder ({@link #index}): food short for the town's size makes it dear (and
 * the townsfolk grumble and sour), plenty makes it cheap (and they're content). A realm with a full larder sells its
 * surplus to the merchants, and one running short buys bread in out of its treasury ({@link #trade}). Nothing to be had
 * at all, and they go without.
 * <p>
 * Each cookhouse and tavern takes on a <b>cook</b> ({@link Professions#COOK}) from the town's idle villagers. By day
 * the cook works there turning the storehouse's raw food into dishes (bread, baked potatoes, cooked meat, soups and
 * stews, pies), and sells meals to players, and buys their produce while the town is short, at the day's prices.
 */
public final class Meals {
	/** How often meals are thought about (ticks); how far round a seat. */
	static final int EVERY = 20;
	static final double WATCHED = 80.0;
	/** Mealtimes, by the time of day: lunch (Routines' hour) and supper. */
	static final long LUNCH_FROM = 5000L, LUNCH_TO = 7000L, SUPPER_FROM = 10000L, SUPPER_TO = 11800L;
	/** The chance each think that someone who hasn't eaten yet goes for their meal. */
	static final float START_CHANCE = 0.06F;
	/** Their pace; how close is there; the longest they try to get there (ticks); how long a meal takes (ticks). */
	static final float WALK = 0.55F;
	static final double ARRIVE = 2.5;
	static final int TRAVEL_TICKS = 900, MEAL_MIN = 200, MEAL_MORE = 200;
	/** A stove counts this near their bed. */
	static final int STOVE_REACH = 6;
	/** Meals a home cook gets out of an item of raw food. */
	static final int HOME_PORTIONS = 2;
	/** Servings in the eatery's pot from an item: a cook's dish, a soup or stew, or anything else. */
	static final int DISH_SERVINGS = 3, STEW_SERVINGS = 4, PLAIN_SERVINGS = 2;
	/** A meal's price in bits ({@link #BITS} to an emerald) when food is as it should be; the realm's tax on townsfolk's meals (percent). */
	static final int MEAL_BITS = 2, BITS = 16, TAX_PERCENT = 25;
	/** The food a town wants in store (items): per head, and at least. The price index's bounds (percent). */
	static final int WANT_PER_HEAD = 2, WANT_LEAST = 16, INDEX_LEAST = 50, INDEX_MOST = 300;
	/** Dear from this index (percent; they grumble), cheap below this (they're content); the chance of saying so. */
	static final int DEAR = 175, CHEAP = 75;
	static final float SAY_CHANCE = 0.35F;
	static final int DEAR_MOOD = -1, CHEAP_MOOD = 1, TAVERN_MOOD = 1, WENT_WITHOUT_MOOD = -2;
	/** The food trade (realms, once a day): surplus over this many times the want sold, this many items at most, this many to an emerald. */
	static final int SURPLUS_TIMES = 3, TRADE_MOST = 32, SELL_PER_EMERALD = 8;
	/** ...and short below half the want: bread bought in, this many loaves to an emerald. */
	static final int BUY_PER_EMERALD = 4;
	/** The food trade's hour (time of day). */
	static final long TRADE_AT = 1000L;
	/** A cook: their hours (time of day), a batch per this many ticks, how near the eatery they're at work, how far they're sought. */
	static final long COOK_FROM = 3000L, COOK_TO = 11800L;
	static final int COOK_EVERY = 1200;
	static final double AT_KITCHEN = 3.5;
	/** How often cooks are seen to (ticks). */
	static final int STAFF_EVERY = 200;
	/** A cook's wares: their uses before restocking (each dawn), and the xp a trade gives. */
	static final int WARES_USES = 12, WARES_XP = 2;
	/** Lines each kind of meal has (lang {@code meals.jaymoskingdoms.<kind>.N}). */
	static final int LINES = 4;
	static final double SPEECH_RANGE = 12.0;

	/** Kitchen's hot blocks (a pot or pan over them is a stove), by tag; empty without Kitchen. */
	private static final TagKey<net.minecraft.world.level.block.Block> HEAT = TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
		net.minecraft.resources.Identifier.fromNamespaceAndPath("jaymoskitchen", "heat_sources"));

	/** What a home cook takes for a meal, first to last (then anything edible). */
	private static final List<Item> RAW = List.of(Items.WHEAT, Items.POTATO, Items.CARROT, Items.BEETROOT, Items.BEEF, Items.PORKCHOP, Items.MUTTON,
		Items.CHICKEN, Items.RABBIT, Items.COD, Items.SALMON, Items.APPLE);
	/** What an eatery serves first (its cook's dishes), then anything edible. */
	private static final List<Item> DISHES = List.of(Items.RABBIT_STEW, Items.MUSHROOM_STEW, Items.BEETROOT_SOUP, Items.PUMPKIN_PIE, Items.COOKED_BEEF,
		Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_CHICKEN, Items.COOKED_RABBIT, Items.COOKED_COD, Items.COOKED_SALMON, Items.BREAD,
		Items.BAKED_POTATO);
	/** A cook's meat and fish, raw and cooked. */
	private static final Item[][] ROASTS = {
		{Items.BEEF, Items.COOKED_BEEF}, {Items.PORKCHOP, Items.COOKED_PORKCHOP}, {Items.MUTTON, Items.COOKED_MUTTON},
		{Items.CHICKEN, Items.COOKED_CHICKEN}, {Items.RABBIT, Items.COOKED_RABBIT}, {Items.COD, Items.COOKED_COD}, {Items.SALMON, Items.COOKED_SALMON}};
	/** A batch: loaves (three wheat each), potatoes baked, joints roasted; bowls carved from a log. */
	static final int BREAD_BATCH = 4, BAKE_BATCH = 6, ROAST_BATCH = 4, BOWLS_FROM_LOG = 4;

	private enum Way { HOME, EATERY, MARKET }

	private static final class Meal {
		final int faction;
		final Way way;
		final BlockPos to;
		/** The stove (a home cook's) or the counter. */
		final BlockPos at;
		final long started;
		long arrived = -1L;
		final int length;
		Item dish = Items.BREAD;
		boolean served;
		final boolean tavern;

		Meal(int faction, Way way, BlockPos to, BlockPos at, long started, int length, boolean tavern) {
			this.faction = faction;
			this.way = way;
			this.to = to;
			this.at = at;
			this.started = started;
			this.length = length;
			this.tavern = tavern;
		}
	}

	/** An eatery: its building, where meals are handed over, and whether it's the tavern. */
	record Eatery(Works.Built built, BlockPos counter, boolean tavern) {
	}

	private static final Map<UUID, Meal> EATING = new HashMap<>();
	/** Who's had which meal (day * 2 + the meal). */
	private static final Map<UUID, Long> FED = new HashMap<>();
	/** A home cook's portions left of the last item they took. */
	private static final Map<UUID, Integer> PANTRY = new HashMap<>();
	/** A stove by their bed, looked for once a day: {day, pos or MISSING}. */
	private static final Map<UUID, long[]> STOVES = new HashMap<>();
	private static final long MISSING = Long.MIN_VALUE;
	/** Per settlement: servings in the eateries' pot, and the dish they are; the till's bits not yet a whole emerald; the food trade's last day. */
	private static final Map<Integer, Integer> POT = new HashMap<>();
	private static final Map<Integer, Item> POT_DISH = new HashMap<>();
	private static final Map<Integer, Integer> TILL = new HashMap<>();
	private static final Map<Integer, Long> TRADED = new HashMap<>();
	/** A cook: the day their wares were last set out, and their last batch (game time). */
	private static final Map<UUID, Long> WARES_DAY = new HashMap<>();
	private static final Map<UUID, Long> LAST_BATCH = new HashMap<>();

	private Meals() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Meals::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			EATING.clear();
			FED.clear();
			PANTRY.clear();
			STOVES.clear();
			POT.clear();
			POT_DISH.clear();
			TILL.clear();
			TRADED.clear();
			WARES_DAY.clear();
			LAST_BATCH.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("larder").executes(Meals::larder))));
	}

	/** {@code v} is at a meal just now (going to it, cooking, eating). */
	static boolean eating(Villager v) {
		return EATING.containsKey(v.getUUID());
	}

	static boolean cook(Villager v) {
		return v.getVillagerData().profession().value() == Professions.COOK;
	}

	// ---- prices ----------------------------------------------------------------------------------------------------

	/** The food {@code f} wants in store for its size. */
	static int want(Faction f) {
		return Math.max(WANT_LEAST, f.holdings().population() * WANT_PER_HEAD);
	}

	/** The price of food in {@code f} (percent of usual): the want over the stock, within bounds; usual if its larder isn't loaded. */
	static int index(ServerLevel level, Faction f) {
		if (!Storage.loaded(level, f)) {
			return 100;
		}
		int stock = Storage.food(level, f);
		return Math.max(INDEX_LEAST, Math.min(INDEX_MOST, want(f) * 100 / Math.max(1, stock)));
	}

	/** {@code bits} paid to {@code f}'s till; whole emeralds go to its treasury (a realm's: a village keeps no purse). */
	static void pay(MinecraftServer server, Faction f, int bits) {
		if (!f.type().isRealm() || bits <= 0) {
			return;
		}
		int till = TILL.getOrDefault(f.id(), 0) + bits;
		int emeralds = till / BITS;
		TILL.put(f.id(), till % BITS);
		if (emeralds > 0) {
			Factions.update(server, f.id(), x -> x.withTreasury(x.treasury() + emeralds));
		}
	}

	// ---- the day ---------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		long t = Royals.timeOfDay(level);
		long day = Festivals.day(level);
		int slot = t >= LUNCH_FROM && t < LUNCH_TO ? 0 : t >= SUPPER_FROM && t < SUPPER_TO ? 1 : -1;
		RandomSource random = level.getRandom();
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			if (f.type().isRealm() && t >= TRADE_AT && TRADED.getOrDefault(f.id(), -1L) != day) {
				TRADED.put(f.id(), day);
				trade(level, f);
			}
			List<Eatery> eateries = eateries(level, f);
			if (now % STAFF_EVERY == 0) {
				staff(level, f, eateries, day);
			}
			List<Villager> folk = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(WATCHED), Villager::isAlive);
			for (Villager v : folk) {
				if (cook(v) && !Visitors.is(v)) {
					cookAt(level, f, v, eateries, t, now);
				}
				Meal m = EATING.get(v.getUUID());
				if (m != null) {
					if (m.faction != f.id()) {
						continue;
					}
					if (!fit(v, t) || !step(level, f, v, m, now)) {
						EATING.remove(v.getUUID());
					}
					continue;
				}
				if (slot < 0 || FED.getOrDefault(v.getUUID(), -1L) == day * 2 + slot || !fit(v, t) || random.nextFloat() >= START_CHANCE) {
					continue;
				}
				if (Festivals.going(v)) {
					// (a festival's table feeds them)
					FED.put(v.getUUID(), day * 2 + slot);
					continue;
				}
				FED.put(v.getUUID(), day * 2 + slot);
				start(level, f, v, eateries, day, now);
			}
		}
		EATING.values().removeIf(m -> now - m.started > TRAVEL_TICKS + MEAL_MIN + MEAL_MORE + 200);
	}

	/** Free for a meal: awake, unafraid, not fighting or talking; a trade or a shift breaks off for it, but not a guard's watch or a ruler's court. */
	private static boolean fit(Villager v, long t) {
		if (!Routines.idle(v) || Rulers.is(v) || McaCompat.following(v) != null || v.hasAttached(Emigrants.MOVE) || Downed.isDowned(v)) {
			return false;
		}
		if (v instanceof Worker w && w.role() >= 0) {
			// (the realm's folk eat from the storehouse on their own hours)
			return false;
		}
		String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		return !job.equals("guard") && !job.equals("archer");
	}

	/** {@code v} goes for their meal: home to their stove if they've one and food to cook, else to buy one. */
	private static void start(ServerLevel level, Faction f, Villager v, List<Eatery> eateries, long day, long now) {
		RandomSource random = level.getRandom();
		int length = MEAL_MIN + random.nextInt(MEAL_MORE);
		boolean visitor = Visitors.is(v);
		BlockPos stove = visitor ? null : stove(level, v, day);
		Meal m = null;
		if (stove != null && pantry(level, f, v)) {
			m = new Meal(f.id(), Way.HOME, Construction.standable(level, stove.relative(direction(level, stove, v)), 2), stove, now, length, false);
		} else if (!eateries.isEmpty()) {
			Eatery e = nearest(eateries, v);
			BlockPos seat = Construction.standable(level, e.counter().offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2), 3);
			m = new Meal(f.id(), Way.EATERY, seat, e.counter(), now, length, e.tavern());
		} else {
			BlockPos stall = Construction.standable(level, f.seat().offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4), 3);
			m = new Meal(f.id(), Way.MARKET, stall, f.seat(), now, length, false);
		}
		EATING.put(v.getUUID(), m);
		walk(v, m.to);
	}

	/** Which side of the stove to stand: the one nearer {@code v}. */
	private static net.minecraft.core.Direction direction(ServerLevel level, BlockPos stove, Villager v) {
		Vec3 d = v.position().subtract(Vec3.atCenterOf(stove));
		return net.minecraft.core.Direction.getApproximateNearest(d.x, 0.0, d.z);
	}

	/** One think at a meal; false once it's over. */
	private static boolean step(ServerLevel level, Faction f, Villager v, Meal m, long now) {
		RandomSource random = level.getRandom();
		if (m.arrived < 0) {
			boolean there = v.position().distanceToSqr(Vec3.atBottomCenterOf(m.to)) <= ARRIVE * ARRIVE;
			if (!there && now - m.started < TRAVEL_TICKS) {
				if (v.getNavigation().isDone()) {
					walk(v, m.to);
				}
				return true;
			}
			// (there, or too long on the way: they eat where they've got to)
			m.arrived = now;
			if (!serve(level, f, v, m)) {
				say(level, v, "none", random);
				McaCompat.cheer(v, WENT_WITHOUT_MOOD);
				return false;
			}
		}
		v.getNavigation().stop();
		v.getLookControl().setLookAt(m.at.getX() + 0.5, m.at.getY() + 0.5, m.at.getZ() + 0.5);
		long in = now - m.arrived;
		boolean cooking = m.way == Way.HOME && in < m.length / 2;
		if (cooking) {
			if (random.nextBoolean()) {
				level.sendParticles(ParticleTypes.SMOKE, m.at.getX() + 0.5, m.at.getY() + 1.1, m.at.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.01);
			}
			if (random.nextInt(4) == 0) {
				v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				level.playSound(null, m.at, random.nextBoolean() ? SoundEvents.CAMPFIRE_CRACKLE : SoundEvents.SMOKER_SMOKE, SoundSource.BLOCKS, 0.5F, 1.0F);
			}
		} else if (random.nextInt(3) != 0) {
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, m.dish), v.getX(), v.getEyeY() - 0.2, v.getZ(), 5, 0.1, 0.1, 0.1, 0.05);
			level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.5F, 1.0F);
		}
		if (in >= m.length) {
			level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.PLAYER_BURP, SoundSource.NEUTRAL, 0.3F, 1.2F);
			return false;
		}
		return true;
	}

	/** The meal itself: cooked from the home cook's share, or bought (and paid for). False if there's none to be had. */
	private static boolean serve(ServerLevel level, Faction f, Villager v, Meal m) {
		RandomSource random = level.getRandom();
		if (m.way == Way.HOME) {
			int left = PANTRY.getOrDefault(v.getUUID(), 0);
			PANTRY.put(v.getUUID(), Math.max(0, left - 1));
			m.dish = random.nextBoolean() ? Items.BREAD : Items.BAKED_POTATO;
			if (random.nextFloat() < SAY_CHANCE) {
				say(level, v, v.isBaby() ? "home_child" : "home", random);
			}
			return true;
		}
		Item dish = takeServing(level, f);
		if (dish == null) {
			return false;
		}
		m.dish = dish;
		m.served = true;
		int index = index(level, f);
		int price = Math.max(1, MEAL_BITS * index / 100);
		boolean visitor = Visitors.is(v);
		pay(level.getServer(), f, visitor ? price : Math.max(1, price * TAX_PERCENT / 100));
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_TRADE, SoundSource.NEUTRAL, 0.5F, 1.0F);
		if (visitor) {
			if (random.nextFloat() < SAY_CHANCE) {
				say(level, v, "visitor", random);
			}
			return true;
		}
		if (index >= DEAR) {
			McaCompat.cheer(v, DEAR_MOOD);
			if (random.nextFloat() < SAY_CHANCE) {
				say(level, v, "dear", random);
			}
		} else if (index <= CHEAP) {
			McaCompat.cheer(v, CHEAP_MOOD);
			if (random.nextFloat() < SAY_CHANCE) {
				say(level, v, "cheap", random);
			}
		} else if (random.nextFloat() < SAY_CHANCE) {
			say(level, v, m.tavern ? "tavern" : m.way == Way.EATERY ? "eatery" : "market", random);
		}
		if (m.tavern) {
			// (good company over a meal)
			McaCompat.cheer(v, TAVERN_MOOD);
		}
		return true;
	}

	/** A serving from {@code f}'s pot, filled from the storehouse (its cook's dishes first); the dish, or null if there's no food. */
	private static @Nullable Item takeServing(ServerLevel level, Faction f) {
		int pot = POT.getOrDefault(f.id(), 0);
		if (pot <= 0) {
			if (!Storage.loaded(level, f)) {
				return null;
			}
			ItemStack food = ItemStack.EMPTY;
			for (Item dish : DISHES) {
				food = Storage.take(level, f, dish, 1);
				if (!food.isEmpty()) {
					break;
				}
			}
			if (food.isEmpty()) {
				food = Storage.take(level, f, Storage::isFood, 1);
			}
			if (food.isEmpty()) {
				return null;
			}
			Item item = food.getItem();
			if (item == Items.MUSHROOM_STEW || item == Items.RABBIT_STEW || item == Items.BEETROOT_SOUP) {
				// (the bowl goes back on the shelf)
				Storage.put(level, f, Items.BOWL, 1);
			}
			pot = servings(item);
			POT_DISH.put(f.id(), item);
		}
		POT.put(f.id(), pot - 1);
		return POT_DISH.getOrDefault(f.id(), Items.BREAD);
	}

	/** The servings an item makes in the pot. */
	static int servings(Item item) {
		if (item == Items.MUSHROOM_STEW || item == Items.RABBIT_STEW || item == Items.BEETROOT_SOUP || item == Items.PUMPKIN_PIE) {
			return STEW_SERVINGS;
		}
		return DISHES.contains(item) ? DISH_SERVINGS : PLAIN_SERVINGS;
	}

	/** A home cook has a share to cook: what's left of the last, else an item of raw food from the storehouse. */
	private static boolean pantry(ServerLevel level, Faction f, Villager v) {
		if (PANTRY.getOrDefault(v.getUUID(), 0) > 0) {
			return true;
		}
		if (!Storage.loaded(level, f)) {
			return false;
		}
		ItemStack got = ItemStack.EMPTY;
		for (Item raw : RAW) {
			got = Storage.take(level, f, raw, 1);
			if (!got.isEmpty()) {
				break;
			}
		}
		if (got.isEmpty()) {
			got = Storage.take(level, f, Storage::isFood, 1);
		}
		if (got.isEmpty()) {
			return false;
		}
		PANTRY.put(v.getUUID(), HOME_PORTIONS);
		return true;
	}

	/** A stove by {@code v}'s bed (looked for once a day), or null: a furnace, smoker, lit campfire, or one of Kitchen's hot blocks. */
	static @Nullable BlockPos stove(ServerLevel level, Villager v, long day) {
		long[] known = STOVES.get(v.getUUID());
		if (known != null && known[0] == day) {
			return known[1] == MISSING ? null : BlockPos.of(known[1]);
		}
		BlockPos found = null;
		GlobalPos home = McaCompat.home(v).orElse(null);
		if (home != null && home.dimension() == level.dimension() && level.isLoaded(home.pos())) {
			double best = Double.MAX_VALUE;
			for (BlockPos p : BlockPos.betweenClosed(home.pos().offset(-STOVE_REACH, -2, -STOVE_REACH), home.pos().offset(STOVE_REACH, 2, STOVE_REACH))) {
				BlockState state = level.getBlockState(p);
				boolean hot = state.is(Blocks.FURNACE) || state.is(Blocks.SMOKER) || state.is(HEAT)
					|| state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
				double d = p.distSqr(home.pos());
				if (hot && d < best) {
					best = d;
					found = p.immutable();
				}
			}
		}
		STOVES.put(v.getUUID(), new long[] {day, found == null ? MISSING : found.asLong()});
		return found;
	}

	// ---- eateries and cooks --------------------------------------------------------------------------------------

	/** {@code f}'s standing cookhouses and taverns. */
	static List<Eatery> eateries(ServerLevel level, Faction f) {
		List<Eatery> out = new ArrayList<>();
		for (Works.Built b : Works.of(level.getServer(), f.id()).built()) {
			boolean tavern = b.entry().equals(Planner.TAVERN);
			if (b.ruined() != 0 || !tavern && !b.entry().equals("cookhouse")) {
				continue;
			}
			out.add(new Eatery(b, counter(level, b), tavern));
		}
		return out;
	}

	/** Where an eatery's meals are handed over: its smoker (the kitchen), else its middle. */
	private static BlockPos counter(ServerLevel level, Works.Built b) {
		BlockPos top = b.top();
		if (level.isLoaded(b.min()) && level.isLoaded(top)) {
			for (BlockPos p : BlockPos.betweenClosed(b.min(), new BlockPos(top.getX(), Math.min(top.getY(), b.min().getY() + 3), top.getZ()))) {
				if (level.getBlockState(p).is(Blocks.SMOKER)) {
					return p.immutable();
				}
			}
		}
		return new BlockPos((b.min().getX() + b.max().getX()) / 2, b.min().getY() + 1, (b.min().getZ() + b.max().getZ()) / 2);
	}

	private static Eatery nearest(List<Eatery> eateries, Villager v) {
		Eatery best = eateries.get(0);
		for (Eatery e : eateries) {
			if (e.counter().distSqr(v.blockPosition()) < best.counter().distSqr(v.blockPosition())) {
				best = e;
			}
		}
		return best;
	}

	/** Each eatery has a cook: an idle townsperson takes it on if it hasn't (and every cook's wares are set out each day). */
	private static void staff(ServerLevel level, Faction f, List<Eatery> eateries, long day) {
		List<Villager> about = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(Economy.POPULATION_RANGE),
			v -> v.isAlive() && !Visitors.is(v));
		int cooks = 0;
		for (Villager v : about) {
			if (cook(v)) {
				cooks++;
				if (WARES_DAY.getOrDefault(v.getUUID(), -1L) != day) {
					WARES_DAY.put(v.getUUID(), day);
					wares(level, f, v);
				}
			}
		}
		if (cooks >= eateries.size()) {
			return;
		}
		for (Villager v : about) {
			if (!v.isBaby() && v.getVillagerData().profession().is(VillagerProfession.NONE) && !(v instanceof Worker w && w.role() >= 0) && !Rulers.is(v)
				&& (f.leader().isEmpty() || !f.leader().get().equals(v.getUUID())) && !McaCompat.playerFamily(v) && McaCompat.following(v) == null
				&& !v.hasAttached(Emigrants.MOVE)) {
				v.setVillagerData(v.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(Professions.COOK)));
				// (a little experience, so the game doesn't take the trade off them for want of a job site)
				v.setVillagerXp(Math.max(1, v.getVillagerXp()));
				v.setPersistenceRequired();
				WARES_DAY.put(v.getUUID(), day);
				wares(level, f, v);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2.1, v.getZ(), 5, 0.3, 0.2, 0.3, 0.0);
				say(level, v, "hired", level.getRandom());
				return;
			}
		}
	}

	/** By day, a cook keeps to the nearest eatery and turns raw food in the storehouse into dishes there. */
	private static void cookAt(ServerLevel level, Faction f, Villager v, List<Eatery> eateries, long t, long now) {
		if (eateries.isEmpty() || t < COOK_FROM || t >= COOK_TO || v.isSleeping() || EATING.containsKey(v.getUUID()) || !Routines.idle(v)
			|| v.getTradingPlayer() != null) {
			return;
		}
		Eatery e = nearest(eateries, v);
		Vec3 at = Vec3.atCenterOf(e.counter());
		if (v.distanceToSqr(at) > AT_KITCHEN * AT_KITCHEN) {
			if (v.getNavigation().isDone()) {
				walk(v, Construction.standable(level, e.counter(), 2));
			}
			return;
		}
		v.getLookControl().setLookAt(at.x, at.y, at.z);
		RandomSource random = level.getRandom();
		if (random.nextInt(3) == 0) {
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			v.playWorkSound();
			level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.6, at.z, 3, 0.2, 0.1, 0.2, 0.01);
		}
		long last = LAST_BATCH.getOrDefault(v.getUUID(), 0L);
		if (now - last < COOK_EVERY || !Storage.loaded(level, f)) {
			return;
		}
		LAST_BATCH.put(v.getUUID(), now);
		if (batch(level, f, random)) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, v.getX(), v.getY() + 2.1, v.getZ(), 3, 0.2, 0.1, 0.2, 0.0);
		}
	}

	/** A cook's batch of dishes from the storehouse's raw food, starting from a different dish each time (true if they made any). */
	private static boolean batch(ServerLevel level, Faction f, RandomSource random) {
		int first = random.nextInt(5);
		for (int i = 0; i < 5; i++) {
			if (dish(level, f, (first + i) % 5)) {
				return true;
			}
		}
		return false;
	}

	private static boolean dish(ServerLevel level, Faction f, int which) {
		switch (which) {
			case 0 -> {
				int loaves = Math.min(BREAD_BATCH, Storage.count(level, f, Items.WHEAT) / 3);
				if (loaves <= 0) {
					return false;
				}
				Storage.take(level, f, Items.WHEAT, loaves * 3);
				Storage.put(level, f, Items.BREAD, loaves);
				return true;
			}
			case 1 -> {
				return convert(level, f, Items.POTATO, Items.BAKED_POTATO, BAKE_BATCH);
			}
			case 2 -> {
				for (Item[] roast : ROASTS) {
					if (Storage.count(level, f, roast[0]) > 0) {
						return convert(level, f, roast[0], roast[1], ROAST_BATCH);
					}
				}
				return false;
			}
			case 3 -> {
				if (!bowl(level, f)) {
					return false;
				}
				if (Storage.count(level, f, Items.BEETROOT) >= 6) {
					Storage.take(level, f, Items.BEETROOT, 6);
					Storage.take(level, f, Items.BOWL, 1);
					Storage.put(level, f, Items.BEETROOT_SOUP, 1);
					return true;
				}
				if (Storage.count(level, f, Items.RED_MUSHROOM) > 0 && Storage.count(level, f, Items.BROWN_MUSHROOM) > 0) {
					Storage.take(level, f, Items.RED_MUSHROOM, 1);
					Storage.take(level, f, Items.BROWN_MUSHROOM, 1);
					Storage.take(level, f, Items.BOWL, 1);
					Storage.put(level, f, Items.MUSHROOM_STEW, 1);
					return true;
				}
				return false;
			}
			default -> {
				if (Storage.count(level, f, Items.PUMPKIN) > 0 && Storage.count(level, f, Items.SUGAR) > 0 && Storage.count(level, f, Items.EGG) > 0) {
					Storage.take(level, f, Items.PUMPKIN, 1);
					Storage.take(level, f, Items.SUGAR, 1);
					Storage.take(level, f, Items.EGG, 1);
					Storage.put(level, f, Items.PUMPKIN_PIE, 1);
					return true;
				}
				return false;
			}
		}
	}

	/** A bowl in the storehouse, carving some from one of its logs if there's none. */
	private static boolean bowl(ServerLevel level, Faction f) {
		if (Storage.count(level, f, Items.BOWL) > 0) {
			return true;
		}
		Item log = Production.logItem(level, f);
		if (Storage.take(level, f, log, 1).isEmpty()) {
			return false;
		}
		Storage.put(level, f, Items.BOWL, BOWLS_FROM_LOG);
		return true;
	}

	private static boolean convert(ServerLevel level, Faction f, Item from, Item to, int most) {
		ItemStack taken = Storage.take(level, f, from, most);
		if (taken.isEmpty()) {
			return false;
		}
		int left = Storage.put(level, f, to, taken.getCount());
		if (left > 0) {
			Storage.put(level, f, from, left);
		}
		return left < taken.getCount();
	}

	/**
	 * A cook's wares at today's prices: meals for an emerald (more when food is cheap), and while the town is short, the
	 * town's want of players' produce (wheat, potatoes, carrots, meat) for an emerald, fewer the shorter it is.
	 */
	static void wares(ServerLevel level, Faction f, Villager v) {
		int index = index(level, f);
		MerchantOffers offers = new MerchantOffers();
		int loaves = Math.max(1, Math.min(12, 600 / index));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, loaves), WARES_USES, WARES_XP, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BAKED_POTATO, Math.max(1, Math.min(16, 800 / index))), WARES_USES,
			WARES_XP, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.COOKED_BEEF, Math.max(1, Math.min(8, 400 / index))), WARES_USES,
			WARES_XP, 0.05F));
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, Math.max(1, index / 100)), new ItemStack(Items.MUSHROOM_STEW), WARES_USES, WARES_XP, 0.05F));
		if (index > 100) {
			int crops = Math.max(4, Math.min(24, 2000 / index));
			offers.add(new MerchantOffer(new ItemCost(Items.WHEAT, crops), new ItemStack(Items.EMERALD), WARES_USES, WARES_XP, 0.05F));
			offers.add(new MerchantOffer(new ItemCost(Items.POTATO, crops), new ItemStack(Items.EMERALD), WARES_USES, WARES_XP, 0.05F));
			offers.add(new MerchantOffer(new ItemCost(Items.CARROT, crops), new ItemStack(Items.EMERALD), WARES_USES, WARES_XP, 0.05F));
			offers.add(new MerchantOffer(new ItemCost(Items.BEEF, Math.max(2, crops / 2)), new ItemStack(Items.EMERALD), WARES_USES, WARES_XP, 0.05F));
		}
		v.setOffers(offers);
	}

	/** A trade with one of the cooks: produce sold to them goes into the town's storehouse, and the meals they sell come out of it. */
	public static void traded(Villager v, MerchantOffer offer) {
		if (!cook(v) || !(v.level() instanceof ServerLevel level)) {
			return;
		}
		Faction f = Factions.around(level.getServer(), level, v.blockPosition(), WATCHED);
		if (f == null || f.fallen() || !Storage.loaded(level, f)) {
			return;
		}
		ItemStack paid = offer.getCostA();
		if (Storage.isFood(paid)) {
			Storage.put(level, f, paid.copy());
		}
		ItemStack sold = offer.getResult();
		if (Storage.isFood(sold)) {
			Storage.take(level, f, sold.getItem(), sold.getCount());
		}
	}

	// ---- the food trade --------------------------------------------------------------------------------------------

	/**
	 * Once a day: a realm with a full larder sells its surplus to the merchants (into its treasury), and one running short
	 * buys bread in out of its treasury, as much as it can afford.
	 */
	static void trade(ServerLevel level, Faction f) {
		if (!Storage.loaded(level, f)) {
			return;
		}
		MinecraftServer server = level.getServer();
		int stock = Storage.food(level, f);
		int want = want(f);
		if (stock > want * SURPLUS_TIMES) {
			int sell = Math.min(TRADE_MOST, stock - want * 2);
			int sold = 0;
			for (int i = 0; i < sell; i++) {
				// (what keeps least well goes first: the raw food)
				ItemStack s = ItemStack.EMPTY;
				for (Item raw : RAW) {
					s = Storage.take(level, f, raw, 1);
					if (!s.isEmpty()) {
						break;
					}
				}
				if (s.isEmpty()) {
					s = Storage.take(level, f, Storage::isFood, 1);
				}
				if (s.isEmpty()) {
					break;
				}
				sold++;
			}
			int emeralds = sold / SELL_PER_EMERALD;
			if (emeralds > 0) {
				Factions.update(server, f.id(), x -> x.withTreasury(x.treasury() + emeralds));
			}
			JaymosKingdoms.LOGGER.debug("{} sold {} food for {} emeralds", f.name(), sold, emeralds);
		} else if (stock < want / 2 && f.treasury() > 0) {
			int need = Math.min(TRADE_MOST, want - stock);
			long cost = Math.min(f.treasury(), (need + BUY_PER_EMERALD - 1) / BUY_PER_EMERALD);
			int bought = (int)Math.min(need, cost * BUY_PER_EMERALD);
			int lost = Storage.put(level, f, Items.BREAD, bought);
			long paid = (bought - lost + BUY_PER_EMERALD - 1) / BUY_PER_EMERALD;
			if (paid > 0) {
				Factions.update(server, f.id(), x -> x.withTreasury(Math.max(0L, x.treasury() - paid)));
			}
			JaymosKingdoms.LOGGER.debug("{} bought {} bread for {} emeralds", f.name(), bought - lost, paid);
		}
	}

	// ---- odds and ends -----------------------------------------------------------------------------------------------

	private static void walk(Villager v, BlockPos to) {
		BehaviorUtils.setWalkAndLookTargetMemories(v, to, WALK, 1);
	}

	private static void say(ServerLevel level, Villager v, String kind, RandomSource random) {
		Speech.say(level, v, Component.translatable("meals.jaymoskingdoms." + kind + "." + random.nextInt(LINES)), Speech.WHITE, SPEECH_RANGE);
	}

	/** {@code /kingdom larder}: the settlement round you: its food in store and wanted, the price of a meal, its eateries and cooks, today's visitors. */
	private static int larder(CommandContext<CommandSourceStack> c) {
		MinecraftServer server = c.getSource().getServer();
		ServerLevel level = Factions.level(server);
		Faction f = Factions.around(server, c.getSource().getLevel(), BlockPos.containing(c.getSource().getPosition()), 160.0);
		if (f == null || f.fallen() || f.type() == FactionType.BARBARIAN) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.larder_none"));
			return 0;
		}
		boolean loaded = Storage.loaded(level, f);
		int stock = loaded ? Storage.food(level, f) : -1;
		int cooks = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(Economy.POPULATION_RANGE), v -> v.isAlive() && cook(v)).size();
		int visitors = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(WATCHED), v -> v.isAlive() && Visitors.is(v)).size();
		Component food = loaded ? Component.literal(Integer.toString(stock)) : Component.translatable("message.jaymoskingdoms.larder_unseen");
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.larder", f.name(), food, want(f), index(level, f),
			eateries(level, f).size(), cooks, visitors), false);
		return 1;
	}
}
