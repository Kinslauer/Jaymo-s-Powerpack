package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Difficulty;
import com.jaymoskingdoms.equestrian.Breed;
import com.jaymoskingdoms.equestrian.Equestrian;
import com.jaymoskingdoms.mixin.MobAccessor;
import com.jaymoskingdoms.money.Coinage;
import com.jaymoskingdoms.money.Money;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Caravans for all (Andy, 2026-11-03: "everyone can trade in their own way, even down to using handcarts if they
 * have to"). Every settlement sends its surplus to a trading partner and gets back what it lacks, in the way it can afford:
 * <ul>
 * <li><b>Kingdoms</b> send wagons to other realms and towns (more of them, and mounted guards, the richer they are);</li>
 * <li><b>Villages and towns</b> (blossom villages and havens too) send a villager pushing a {@link Wagon#HANDCART}, slow ({@link #HAND_PACE},
 * {@link #HAND_WALK}) and with
 * a small load, till they are rich enough in folk and coin for a horse and wagon; a haven, which expands hard to make the
 * most of its output, sends more, and its sworn defenders always go along;</li>
 * <li><b>Bandit and illager camps</b> send their loot to camps of their own kind, a cart or two guarded by their own (an illager
 * camp's driven and guarded by real illagers: {@link #illager});</li>
 * <li><b>Ogre holds</b> send an ogre hauling a slow cart of hides, meat and stone to the nearest greenskin camp, and get food
 * back ({@link #ogres});</li>
 * <li><b>Pirate coves</b> send skiffs: a deckhand at the tiller sailing the open water to another cove or a bandit camp, and
 * on foot with handcarts where the way runs over land;</li>
 * <li>fiefdoms ({@link Caravans#start}) and greenskin camps ({@link Caravans#startGreen}) keep the caravans they had;
 * camps that shelter an ogre band load bigger ({@link #OGRE_LOAD}), as their ogres do the hauling.</li>
 * </ul>
 * The caravans are {@link Caravans}' own (its ledger, its road, its robbery); only what a settlement sends, to whom, and
 * what comes of it is here. At the far end the goods go into the partner's storage, the partner's surplus of what the
 * sender is short in comes home, and the difference in worth is paid between their treasuries ({@link #settle}). A player
 * meets a cart by right-clicking it, buys from it with emeralds in hand ({@link #deal}), escorts it by keeping company on
 * its road ({@link #escort}, {@link #reward}), or robs it ({@link #robbed}): the better off the owner, the dearer his good
 * name.
 */
final class TradeRoutes {
	/** How a trade caravan travels: horse and wagon, a handcart on foot, a skiff on the water. */
	static final int CART = 0, HAND = 1, BOAT = 2;

	/** A settlement's trade, by kind: the realm (kingdom), the town (village, haven, blossom village), and the camps. */
	enum Sort {
		REALM(1100.0, 18000L, 64), TOWN(700.0, 14000L, 24), HUMAN(900.0, 24000L, 20), ILLAGER(900.0, 24000L, 20), PIRATE(1200.0, 20000L, 16);

		/** How far a partner may be (blocks), how long between caravans (ticks), and how much of each good it keeps back. */
		final double reach;
		final long every;
		final int keep;

		Sort(double reach, long every, int keep) {
			this.reach = reach;
			this.every = every;
			this.keep = keep;
		}

		boolean camp() {
			return this == HUMAN || this == ILLAGER || this == PIRATE;
		}
	}

	/** What they trade in. */
	enum Ware {
		FOOD(1), TIMBER(1), STONE(1), METAL(5), CLOTH(3), HIDE(3), POWDER(4);

		/** A good's worth in coppers, unless it has a price of its own ({@link #unit}). */
		final int worth;

		Ware(int worth) {
			this.worth = worth;
		}

		boolean test(ItemStack x) {
			Item i = x.getItem();
			return switch (this) {
				case FOOD -> Storage.isFood(x) || x.is(Items.WHEAT);
				case TIMBER -> x.is(ItemTags.LOGS) || x.is(ItemTags.PLANKS);
				case STONE -> i == Items.COBBLESTONE || i == Items.STONE || i == Items.COBBLED_DEEPSLATE || i == Items.ANDESITE || i == Items.DIORITE
					|| i == Items.GRANITE || i == Items.TUFF || i == Items.GRAVEL || i == Items.FLINT || i == Items.CLAY_BALL || i == Items.BRICK;
				case METAL -> i == Items.IRON_INGOT || i == Items.COPPER_INGOT || i == Items.GOLD_INGOT || i == Items.RAW_IRON || i == Items.RAW_COPPER
					|| i == Items.RAW_GOLD || i == Items.COAL || i == Items.REDSTONE || i == Items.LAPIS_LAZULI || i == Items.DIAMOND;
				case CLOTH -> x.is(ItemTags.WOOL) || i == Items.STRING || i == Items.PAPER;
				case HIDE -> i == Items.LEATHER || i == Items.BONE || i == Items.FEATHER || i == Items.RABBIT_HIDE;
				case POWDER -> i == Items.GUNPOWDER;
			};
		}

		static @Nullable Ware of(ItemStack x) {
			for (Ware w : values()) {
				if (w.test(x)) {
					return w;
				}
			}
			return null;
		}

		/** What one {@code x} is worth (coppers): by its kind, but ore and gems have their own prices. */
		static int unit(ItemStack x) {
			Item i = x.getItem();
			// Andy's TFC Hardrock picks: iron is scarce (few, big veins) and diamond all but never found, so they're dear
			if (i == Items.DIAMOND) {
				return 160;
			} else if (i == Items.GOLD_INGOT) {
				return 12;
			} else if (i == Items.IRON_INGOT) {
				return 14;
			} else if (i == Items.RAW_IRON) {
				return 8;
			} else if (i == Items.COAL || i == Items.COPPER_INGOT || i == Items.RAW_COPPER || i == Items.RAW_GOLD || i == Items.REDSTONE
				|| i == Items.LAPIS_LAZULI) {
				return 3;
			}
			Ware w = of(x);
			return w == null ? 1 : w.worth;
		}
	}

	/** What a settlement sends and how: its rig, carts, a cart's load, guards, what it sells and wants, and a levy to draw on when its storehouse is out of reach ((0.7.490) camps and coves too). */
	record Profile(int rig, int carts, int cap, int guards, boolean mounted, EnumSet<Ware> sells, EnumSet<Ware> wants, Item @Nullable [] levy) {
	}

	// ---- tuning ---------------------------------------------------------------------------------------------------------

	/** A handcart's load (items) and a skiff's; a wagon's is {@link Caravans#CART_LOAD}. The most handcarts a town sends. */
	static final int HAND_LOAD = 96, BOAT_LOAD = 320, MOST_HANDCARTS = 2;
	/** A town sends wagons rather than handcarts with this many folk and this many emeralds in its purse. */
	static final int TOWN_WAGON_FOLK = 10;
	static final long TOWN_WAGON_PURSE = 60L;
	/** A haven, which expands to make the most of its output, sends this many more carts, and its sworn defenders go with them. */
	static final int HAVEN_CARTS = 1, HAVEN_GUARDS = 1;
	/** A camp's cart-guards (a pirate cove's go only where there is land under it). */
	static final int CAMP_GUARDS = 2, COVE_GUARDS = 1;
	/** A realm's carts: one, plus one for each wealth tier above poor. */
	static final int REALM_CARTS = 1, COVE_SKIFFS = 2, ILLAGER_CARTS = 2, HUMAN_HANDCARTS = 2;
	/** With its storehouse out of reach, a kingdom or town sends this levy (items), plus this per villager, never more than a rig takes. */
	static final int LEVY_BASE = 64, LEVY_PER_VILLAGER = 2;
	/**
	 * (0.7.490) What a camp or cove draws its levy from while its storehouse is out of reach (it is far from any player as a rule): a
	 * bandit camp's hides, meat and scrap iron, an illager camp's loot (no emeralds), a cove's powder, fish and iron.
	 */
	private static final Item[] HUMAN_LEVY = {Items.LEATHER, Items.LEATHER, Items.BONE, Items.BEEF, Items.COOKED_BEEF, Items.RAW_IRON, Items.COAL};
	private static final Item[] ILLAGER_LEVY = {Items.IRON_INGOT, Items.RAW_IRON, Items.COAL, Items.LEATHER, Items.LEATHER, Items.BONE, Items.FEATHER};
	private static final Item[] PIRATE_LEVY = {Items.GUNPOWDER, Items.GUNPOWDER, Items.COD, Items.SALMON, Items.IRON_INGOT, Items.COAL, Items.RAW_COPPER};
	/** Nothing worth sending below this (items); the least a handcart is sent for. */
	static final int LOAD_LEAST = 24;
	/** A town with less than this of a good it wants asks for it; the carts go home with at most this share of what they brought (percent). */
	static final int LOW = 64, RETURN_SHARE = 60;
	/** Tried again after this long (ticks) when nothing could be sent. */
	static final long RETRY = 3600L;
	/** An ogre band in a camp doubles its carts' load. */
	static final int OGRE_LOAD = 2;
	/** A copper's worth: this many coppers to an emerald. A player pays this much over a good's worth (percent), buys at most this many at once. */
	static final int COPPER_PER_EMERALD = 12, MARKUP = 130, BUY_MOST = 32;
	/** Reputation: robbing a realm's cart (with it, and half with its partner), a town's, a handcart; plundering a camp's (with the nearest settlement); a deal; an escort. */
	static final int REP_ROB_REALM = -60, REP_ROB_TOWN = -45, REP_ROB_HAND = -35, REP_PLUNDER = 8, REP_TRADE = 1, REP_ESCORT = 12;
	/** An escort keeps company this long (ticks) at most this far (blocks) to count; his tip (emeralds, at most); a greenskin cart seen safely along eases the tribe's grudge by this. */
	static final int ESCORT_TICKS = 600, ESCORT_TIP = 3, GREEN_EASE = 4;
	static final double ESCORT_RANGE = 28.0, PLUNDER_RANGE = 500.0;

	/**
	 * (0.7.490) A handcart (or an ogre's cart) is slow: out of sight it goes at this share of a wagon's pace on the road; seen, its
	 * puller walks at this pace (a horse is driven at {@link Caravans#DRIVE_SPEED}), the cart's weight in his hands.
	 */
	static final double HAND_PACE = 0.5, HAND_WALK = 0.45;
	/**
	 * (0.7.490) An ogre hold's trade: an ogre hauls a cart of what the hold has in plenty (hides, meat, hewn stone) to the nearest
	 * greenskin camp, this often (ticks; tried again after {@link #OGRE_RETRY} with no camp near), in this reach (blocks), this many
	 * goods at most and at least; the camp sends him home with some of its food, never more than this share of the haul (percent).
	 */
	static final long OGRE_EVERY = 30000L, OGRE_RETRY = 6000L;
	static final double OGRE_REACH = 700.0;
	static final int OGRE_HAUL_MOST = 192, OGRE_HAUL_LEAST = 64, OGRE_FOOD_BACK = 50;

	static final String PULL_TAG = "jaymoskingdoms.handcart_puller";
	/** (0.7.490) On an illager camp's carter and guards (real illagers: no folk, no villager), so their caravan knows its own. */
	static final String CREW_TAG = "jaymoskingdoms.caravan_crew";
	/** (0.7.490) What an ogre hold hauls. */
	private static final Item[] OGRE_GOODS = {Items.LEATHER, Items.LEATHER, Items.BEEF, Items.MUTTON, Items.BONE, Items.COBBLESTONE, Items.COBBLESTONE, Items.STONE};

	/** Caravan id → who has kept it company on the road (player → ticks). Not saved. */
	private static final Map<Integer, Map<UUID, Integer>> ESCORTS = new HashMap<>();

	private TradeRoutes() {
	}

	static void clear() {
		ESCORTS.clear();
	}

	/** (0.7.490) Caravan {@code id} is off the road (home, lost or broken up): its escorts forgotten. */
	static void forget(int id) {
		ESCORTS.remove(id);
	}

	// ---- who trades, with whom --------------------------------------------------------------------------------------

	/** {@code f}'s kind of trade; null for what trades another way (fiefdoms, greenskin camps) or is gone. */
	static @Nullable Sort sortOf(MinecraftServer server, @Nullable Faction f) {
		if (f == null || f.fallen()) {
			return null;
		}
		return switch (f.type()) {
			case KINGDOM -> Sort.REALM;
			case VILLAGE -> Sort.TOWN;
			case FIEFDOM -> null;
			case BARBARIAN -> {
				Camps.CampData data = Camps.data(server, f.id());
				if (data == null || data.clearedAt() >= 0 || Hordes.greenskin(server, f.id())) {
					yield null;
				}
				yield data.pirate() ? Sort.PIRATE : data.illager() ? Sort.ILLAGER : Sort.HUMAN;
			}
		};
	}

	/** Who a partner of kind {@code b} is to a sender of kind {@code a}: settled places trade with settled places, camps with their own kind (pirates and bandits with each other). */
	static boolean friendly(Sort a, Sort b) {
		return switch (a) {
			case REALM, TOWN -> b == Sort.REALM || b == Sort.TOWN;
			case HUMAN, PIRATE -> b == Sort.HUMAN || b == Sort.PIRATE;
			case ILLAGER -> b == Sort.ILLAGER;
		};
	}

	/**
	 * The partner {@code f}'s caravan goes to: one of the nearest few it could trade with (one that is at peace, with a storehouse and in reach),
	 * drawn by chance so a settlement has several, kingdoms drawing a little more than their share of the road.
	 */
	static @Nullable Faction partner(ServerLevel level, Faction f, Sort sort) {
		MinecraftServer server = level.getServer();
		List<Faction> near = new ArrayList<>();
		for (Faction g : Factions.all(server)) {
			if (g.id() == f.id() || g.fallen() || g.holdings().storage().isEmpty() || g.id() == f.overlord()) {
				continue;
			}
			Sort gs = g.type() == FactionType.FIEFDOM ? Sort.REALM : sortOf(server, g);
			// (0.7.519) a trade charter partner counts as nearer, and in reach from farther
			boolean chartered = Treaties.chartered(server, f, g);
			double reach = sort.reach * (chartered ? Treaties.CHARTER_REACH : 1.0);
			if (gs == null || !friendly(sort, gs) || Raids.horizontal(f.seat(), g.seat()) > reach * reach) {
				continue;
			}
			if (!sort.camp() && (Relations.atWar(f, g) || Economy.atWar(server, g))) {
				continue;
			}
			near.add(g);
		}
		near.sort(Comparator.comparingDouble(g -> Raids.horizontal(f.seat(), g.seat()) / (g.type() == FactionType.KINGDOM ? 1.6 : 1.0)
			/ (Treaties.chartered(server, f, g) ? Treaties.CHARTER_PULL : 1.0)));
		return near.isEmpty() ? null : near.get(level.getRandom().nextInt(Math.min(3, near.size())));
	}

	/** What {@code f} of kind {@code sort} sends and how, by its riches, its people and its blood. */
	static Profile profile(MinecraftServer server, Faction f, Sort sort) {
		boolean haven = !Havens.of(server, f).isEmpty();
		int folk = f.holdings().population();
		return switch (sort) {
			case REALM -> {
				Wealth.Tier tier = Wealth.of(server, f);
				int carts = REALM_CARTS + tier.ordinal();
				yield new Profile(CART, carts, Caravans.CART_LOAD, 1 + carts / 2, tier != Wealth.Tier.POOR,
					EnumSet.of(Ware.FOOD, Ware.TIMBER, Ware.STONE, Ware.METAL, Ware.CLOTH, Ware.HIDE),
					EnumSet.of(Ware.FOOD, Ware.TIMBER, Ware.STONE, Ware.METAL, Ware.CLOTH, Ware.HIDE),
					new Item[] {Items.WHEAT, Items.BREAD, Items.COBBLESTONE, Items.OAK_LOG, Items.IRON_INGOT, Items.STRING, Items.LEATHER});
			}
			case TOWN -> {
				boolean wagons = folk >= TOWN_WAGON_FOLK && purse(Factions.level(server), f) >= TOWN_WAGON_PURSE;
				int carts = wagons ? 1 : Math.min(MOST_HANDCARTS, 1 + folk / 16);
				yield new Profile(wagons ? CART : HAND, carts + (haven ? HAVEN_CARTS : 0), wagons ? Caravans.CART_LOAD : HAND_LOAD,
					(wagons ? 1 : 0) + (haven ? HAVEN_GUARDS : 0), false, EnumSet.of(Ware.FOOD, Ware.CLOTH, Ware.HIDE, Ware.TIMBER),
					EnumSet.of(Ware.STONE, Ware.METAL, Ware.TIMBER, Ware.FOOD),
					new Item[] {Items.WHEAT, Items.BREAD, Items.CARROT, Items.POTATO, Items.STRING, Items.LEATHER});
			}
			case HUMAN -> new Profile(HAND, HUMAN_HANDCARTS, HAND_LOAD, CAMP_GUARDS, false, EnumSet.of(Ware.HIDE, Ware.FOOD, Ware.METAL),
				EnumSet.of(Ware.TIMBER, Ware.METAL, Ware.FOOD), HUMAN_LEVY);
			case ILLAGER -> new Profile(CART, ILLAGER_CARTS, Caravans.CART_LOAD, CAMP_GUARDS, false, EnumSet.of(Ware.METAL, Ware.HIDE),
				EnumSet.of(Ware.FOOD, Ware.TIMBER, Ware.STONE), ILLAGER_LEVY);
			case PIRATE -> new Profile(BOAT, COVE_SKIFFS, BOAT_LOAD, COVE_GUARDS, false, EnumSet.of(Ware.POWDER, Ware.METAL, Ware.FOOD),
				EnumSet.of(Ware.TIMBER, Ware.METAL, Ware.CLOTH), PIRATE_LEVY);
		};
	}

	/** How long {@code home} waits between caravans (ticks). */
	static long every(MinecraftServer server, int home) {
		Sort sort = sortOf(server, Factions.get(server, home));
		return sort == null ? Caravans.CARAVAN_TICKS : sort.every;
	}

	/** Ogre bands in a camp do its hauling: its carts load bigger. */
	static int ogreLoad(MinecraftServer server, Camps.CampData data) {
		return OgreHolds.band(server, data.center()) != null ? OGRE_LOAD : 1;
	}

	/** The rig a trade caravan is seen in at its place on the road: a cove's skiffs where there is water, its handcarts where there is land. */
	static int rigNow(ServerLevel level, Caravans.Caravan c) {
		if (c.rig() == BOAT) {
			return Wagon.water(level, c.at().getX() + 0.5, c.at().getZ() + 0.5) ? BOAT : HAND;
		}
		return c.rig();
	}

	/**
	 * (0.7.490) How fast {@code c} goes out of sight, as a share of a wagon's pace: a handcart (a cove's skiffs on land too, and an ogre's
	 * cart) at {@link #HAND_PACE}; a skiff on unseen water at full pace.
	 */
	static double pace(ServerLevel level, Caravans.Caravan c) {
		if (c.rig() == HAND || c.rig() == BOAT && level.isLoaded(c.at()) && rigNow(level, c) == HAND) {
			return HAND_PACE;
		}
		return 1.0;
	}

	/** Where a skiff ties up at {@code f}: a cove's mooring, else its door. */
	static BlockPos quay(MinecraftServer server, Faction f) {
		PirateCoves.Cove cove = PirateCoves.cove(server, f.id());
		return cove != null ? cove.mooring() : Caravans.start(f);
	}

	private static int capOf(Caravans.Caravan c) {
		return c.rig() == HAND ? HAND_LOAD : c.rig() == BOAT ? BOAT_LOAD : Caravans.CART_LOAD;
	}

	private static boolean in(EnumSet<Ware> set, ItemStack x) {
		Ware w = Ware.of(x);
		return w != null && set.contains(w);
	}

	// ---- sending ------------------------------------------------------------------------------------------------------

	/** Each settlement (not a fiefdom or a greenskin camp: {@link Caravans} has theirs) whose time has come sends its caravan. */
	static void send(ServerLevel level, long now) {
		MinecraftServer server = level.getServer();
		// ((0.7.490) and the ogre holds, which are no one's faction: theirs go by their sites)
		ogres(level, now);
		Caravans.Ledger l = Caravans.ledger(server);
		for (Faction f : Factions.all(server)) {
			Sort sort = sortOf(server, f);
			// ((0.7.490) a haven trading on its own account sends its output by Caravans.startHaven, not from here)
			if (sort == null || Caravans.tradingHaven(server, f)) {
				continue;
			}
			String key = Integer.toString(f.id());
			Long due = l.next().get(key);
			if (due == null) {
				// (first sight of it: its first caravan is spread over its interval, so they don't all set out at once)
				Caravans.later(server, f, now + Math.floorMod(f.id() * 7919L, sort.every));
				l = Caravans.ledger(server);
				continue;
			}
			if (now < due || l.caravans().values().stream().anyMatch(c -> c.home() == f.id())) {
				continue;
			}
			start(level, f, false);
			l = Caravans.ledger(server);
		}
	}

	/** {@code f} sends a caravan now, if it has a partner and goods to send. {@code force}: whatever the time and the load. */
	static Caravans.@Nullable Caravan start(ServerLevel level, Faction f, boolean force) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		Sort sort = sortOf(server, f);
		Faction to = sort == null || Economy.atWar(server, f) && !sort.camp() ? null : partner(level, f, sort);
		if (to == null) {
			Caravans.later(server, f, now + RETRY);
			return null;
		}
		if (sort == Sort.ILLAGER && peaceful(level)) {
			// ((0.7.490) on Peaceful no illager stays in the world to drive or guard a cart: the camp keeps its goods home)
			Caravans.later(server, f, now + RETRY);
			return null;
		}
		Profile p = profile(server, f, sort);
		if (Treaties.chartered(server, f, to)) {
			// (0.7.519) and takes an extra cart
			p = new Profile(p.rig(), p.carts() + Treaties.CHARTER_CARTS, p.cap(), p.guards(), p.mounted(), p.sells(), p.wants(), p.levy());
		}
		if (p.rig() == BOAT && (PirateCoves.cove(server, f.id()) == null || PirateCoves.cove(server, to.id()) == null)) {
			// ((0.7.490) skiffs only from a mooring to a mooring: to a partner inland, the cove's trade goes by handcart)
			p = new Profile(HAND, p.carts(), HAND_LOAD, p.guards(), p.mounted(), p.sells(), p.wants(), p.levy());
		}
		List<Map<String, Integer>> carts;
		java.util.EnumSet<Ware> sells = p.sells();
		if (Storage.loaded(level, f)) {
			carts = Caravans.loadUp(level, f, x -> in(sells, x), sort.keep, p.carts() * p.cap(), p.cap());
		} else if (p.levy() != null) {
			carts = levy(level, f, p);
		} else {
			Caravans.later(server, f, now + RETRY);
			return null;
		}
		int load = carts.stream().mapToInt(c -> c.values().stream().mapToInt(Integer::intValue).sum()).sum();
		if (load < LOAD_LEAST && !force || carts.size() > p.carts()) {
			// (nothing worth the road, or more than it can carry: back what was taken, and wait)
			for (Map<String, Integer> cart : carts) {
				Caravans.deliver(level, f, cart);
			}
			Caravans.later(server, f, now + RETRY);
			return null;
		}
		if (carts.isEmpty()) {
			carts = List.of(Map.of());
		}
		// (0.7.519) a charter partner's caravan goes sooner
		long next = Treaties.chartered(server, f, to) ? now + sort.every * Treaties.CHARTER_EVERY_PERCENT / 100 : now + sort.every;
		Caravans.Caravan c = Caravans.launch(level, f, to, carts, p.guards(), p.mounted(), p.rig(), next);
		JaymosKingdoms.LOGGER.info("{} sends {} {} ({} goods) to {}", f.name(), carts.size(), p.rig() == HAND ? "handcarts" : p.rig() == BOAT ? "skiffs" : "carts", load,
			to.name());
		return c;
	}

	/** A levy of {@code f}'s production, sent while its storehouse is out of reach (nothing is taken from it). */
	private static List<Map<String, Integer>> levy(ServerLevel level, Faction f, Profile p) {
		int n = Math.min(p.carts() * p.cap(), LEVY_BASE + f.holdings().population() * LEVY_PER_VILLAGER);
		return levy(level, p.levy(), n, p.cap());
	}

	/** (0.7.490) {@code n} goods drawn at random from {@code goods}, in carts of {@code cap} each. */
	private static List<Map<String, Integer>> levy(ServerLevel level, Item[] goods, int n, int cap) {
		if (goods.length == 0 || n <= 0) {
			return List.of();
		}
		Map<String, Integer> all = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) {
			all.merge(Caravans.key(goods[level.getRandom().nextInt(goods.length)]), 1, Integer::sum);
		}
		List<Map<String, Integer>> carts = new ArrayList<>();
		Map<String, Integer> cart = new LinkedHashMap<>();
		int in = 0;
		for (Map.Entry<String, Integer> e : all.entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int take = Math.min(left, cap - in);
				cart.merge(e.getKey(), take, Integer::sum);
				in += take;
				left -= take;
				if (in >= cap) {
					carts.add(cart);
					cart = new LinkedHashMap<>();
					in = 0;
				}
			}
		}
		if (!cart.isEmpty()) {
			carts.add(cart);
		}
		return carts;
	}

	// ---- arriving ----------------------------------------------------------------------------------------------------

	/**
	 * At the far end its goods are handed over (a realm partner takes its market dues), the partner's surplus of what the sender is short
	 * in is loaded for home, and the difference is paid between their treasuries; back home, what it brought is put in the storehouse.
	 * Null when the round is done.
	 */
	static Caravans.@Nullable Caravan arrive(ServerLevel level, Caravans.Caravan c, Faction home, Faction lord, long now) {
		MinecraftServer server = level.getServer();
		reward(level, c, home, lord);
		if (c.back()) {
			for (Map<String, Integer> cart : c.carts()) {
				Caravans.deliver(level, home, cart);
			}
			return null;
		}
		long out = 0L;
		for (Map<String, Integer> cart : c.carts()) {
			Caravans.deliver(level, lord, cart);
			out += worth(cart);
		}
		if (lord.type().isRealm()) {
			Treasuries.dues(server, lord, c.load());
		}
		Sort hs = sortOf(server, home);
		Sort ls = sortOf(server, lord);
		EnumSet<Ware> wants = hs == null ? EnumSet.allOf(Ware.class) : wanted(level, home, profile(server, home, hs));
		int cap = capOf(c);
		List<Map<String, Integer>> back = List.of();
		// (0.7.519) a charter partner's carts come home fuller
		int share = RETURN_SHARE + (Treaties.chartered(server, home, lord) ? Treaties.CHARTER_RETURN : 0);
		int most = Math.min(c.carts().size() * cap, Math.max(0, c.load() * share / 100));
		if (Storage.loaded(level, lord)) {
			back = Caravans.loadUp(level, lord, x -> in(wants, x), ls == null ? Sort.REALM.keep : ls.keep, most, cap);
		} else {
			// ((0.7.490) its storehouse out of reach: the carts go home with a levy of what it makes that the sender wants, its stock untouched)
			Sort lk = ls != null ? ls : lord.type() == FactionType.FIEFDOM ? Sort.REALM : null;
			Item[] levy = lk == null ? null : profile(server, lord, lk).levy();
			if (levy != null) {
				Item[] fit = Arrays.stream(levy).filter(i -> in(wants, new ItemStack(i))).toArray(Item[]::new);
				back = levy(level, fit, most, cap);
			}
		}
		long in = 0L;
		for (Map<String, Integer> cart : back) {
			in += worth(cart);
		}
		settle(level, home, lord, out, in);
		return c.turned(fit(back, c.carts().size()), now + Caravans.STOP_TICKS);
	}

	/** The goods {@code home} asks for: those of its wants that it holds little of (all of them while its storehouse is out of reach, or if it is well stocked in every one). */
	private static EnumSet<Ware> wanted(ServerLevel level, Faction home, Profile p) {
		if (!Storage.loaded(level, home)) {
			return p.wants();
		}
		EnumSet<Ware> low = EnumSet.noneOf(Ware.class);
		for (Ware w : p.wants()) {
			if (Storage.count(level, home, w::test) < LOW) {
				low.add(w);
			}
		}
		return low.isEmpty() ? p.wants() : low;
	}

	/** The carts going home: {@code back}, made up to {@code n} (empty ones), or squeezed into {@code n}. */
	private static List<Map<String, Integer>> fit(List<Map<String, Integer>> back, int n) {
		List<Map<String, Integer>> carts = new ArrayList<>(back);
		while (carts.size() < n) {
			carts.add(Map.of());
		}
		while (carts.size() > n) {
			Map<String, Integer> extra = carts.removeLast();
			Map<String, Integer> merged = new HashMap<>(carts.getLast());
			extra.forEach((k, v) -> merged.merge(k, v, Integer::sum));
			carts.set(carts.size() - 1, merged);
		}
		return carts;
	}

	/** What a cart's load is worth (coppers). */
	static long worth(Map<String, Integer> cart) {
		long worth = 0L;
		for (Map.Entry<String, Integer> e : cart.entrySet()) {
			Item item = Caravans.item(e.getKey());
			if (item != null) {
				worth += (long)e.getValue() * Ware.unit(new ItemStack(item));
			}
		}
		return worth;
	}

	/**
	 * (0.7.490) Emeralds {@code f} can pay out: a realm's treasury; a village keeps its money in its storehouse (as haven
	 * trade, wages and {@link HavenGrowth} do), so its storehouse emeralds while that is loaded, else nothing.
	 */
	static long purse(ServerLevel level, Faction f) {
		if (f.type().isRealm()) {
			return Math.max(0L, f.treasury());
		}
		return Storage.loaded(level, f) ? Storage.count(level, f, Items.EMERALD) : 0L;
	}

	/** (0.7.490) Takes up to {@code n} emeralds out of {@code f}'s {@link #purse}; returns what was taken. */
	static long spend(ServerLevel level, Faction f, long n) {
		long take = Math.min(n, purse(level, f));
		if (take <= 0L) {
			return 0L;
		}
		if (f.type().isRealm()) {
			Factions.update(level.getServer(), f.id(), x -> x.withTreasury(x.treasury() - take));
			return take;
		}
		long got = 0L;
		while (got < take) {
			int part = Storage.take(level, f, Items.EMERALD, (int)Math.min(take - got, 64L)).getCount();
			if (part <= 0) {
				break;
			}
			got += part;
		}
		return got;
	}

	/** (0.7.490) Pays {@code n} emeralds into {@code f}'s purse: a realm's treasury, a village's storehouse (held for it till loaded). */
	static void earn(ServerLevel level, Faction f, long n) {
		if (n <= 0L) {
			return;
		}
		if (f.type().isRealm()) {
			Factions.update(level.getServer(), f.id(), x -> x.withTreasury(x.treasury() + n));
			return;
		}
		Caravans.deliver(level, f, Map.of(Caravans.key(Items.EMERALD), (int)Math.min(n, (long)Integer.MAX_VALUE)));
	}

	/** The one who came out ahead is paid the difference in worth by the other, as far as that one's purse goes. */
	static void settle(ServerLevel level, Faction home, Faction lord, long out, long back) {
		MinecraftServer server = level.getServer();
		long diff = (out - back) / COPPER_PER_EMERALD;
		if (diff == 0L) {
			return;
		}
		Faction payer = Factions.get(server, diff > 0 ? lord.id() : home.id());
		Faction payee = Factions.get(server, diff > 0 ? home.id() : lord.id());
		if (payer == null || payee == null) {
			return;
		}
		long sum = spend(level, payer, Math.abs(diff));
		earn(level, payee, sum);
	}

	// ---- its folk -----------------------------------------------------------------------------------------------------

	/**
	 * One of a trade caravan's guards for {@code f} at {@code at}: its own kind of fighter (a camp's own raiders or deckhands; (0.7.490)
	 * an illager camp's own vindicators, real illagers; a town's or realm's guards, MCA's with MCA).
	 */
	static @Nullable PathfinderMob guard(ServerLevel level, Faction f, BlockPos at, float yaw) {
		MinecraftServer server = level.getServer();
		Sort sort = sortOf(server, f);
		if (sort == null || !sort.camp()) {
			if (f.type() == FactionType.VILLAGE) {
				Worker w = McaCompat.escort(level, f, at, yaw, Caravans.TASK, 64);
				if (w != null) {
					return w.mob();
				}
			}
			Worker w = Caravans.guard(level, f, at, yaw);
			return w == null ? null : w.mob();
		}
		if (sort == Sort.ILLAGER) {
			// ((0.7.490) never a folk or a villager by an illager's name: one of the camp's vindicators)
			return illager(level, f, false, at, yaw);
		}
		int role = sort == Sort.PIRATE ? Pirates.DECKHAND : Folk.BARBARIAN;
		Folk brute = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
		if (brute == null) {
			return null;
		}
		brute.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
		brute.setup(role, f, at, 64);
		brute.setTask(Caravans.TASK);
		level.addFreshEntity(brute);
		Worker man = brute;
		if (McaCompat.raider(level, brute) instanceof Worker v) {
			v.setTask(Caravans.TASK);
			man = v;
		}
		return man.mob();
	}

	/** (0.7.490) The world is on Peaceful, where monsters (illagers, ogres) are taken out of the world as soon as they're in it. */
	static boolean peaceful(ServerLevel level) {
		return level.getDifficulty() == Difficulty.PEACEFUL;
	}

	/**
	 * (0.7.490) {@code c} is crewed by monsters (an ogre hold's haul, an illager camp's carts) and the world is on Peaceful: its crew
	 * can't stay in the world, so it never comes into sight and goes on in the record instead of spilling its carts.
	 */
	static boolean unseenCrew(ServerLevel level, Caravans.Caravan c) {
		if (!peaceful(level)) {
			return false;
		}
		if (c.hold() >= 0) {
			return true;
		}
		Faction home = Factions.get(level.getServer(), c.home());
		return home != null && illagers(level.getServer(), home);
	}

	/** (0.7.490) {@code f} is an illager camp: its carts are driven and guarded by its own illagers ({@link #illager}). */
	static boolean illagers(MinecraftServer server, Faction f) {
		return sortOf(server, f) == Sort.ILLAGER;
	}

	/** (0.7.490) {@code mob} is one of an illager camp's caravan crew. */
	static boolean crew(Entity mob) {
		return mob.entityTags().contains(CREW_TAG);
	}

	/**
	 * (0.7.490) One of illager camp {@code f}'s caravan crew at {@code at}: its carter a pillager, its guards vindicators. Real illagers,
	 * not on the camp's roster nor tagged as its raiders (so neither {@link Illagers} nor {@link Raids} sends them home): their caravan
	 * steers them ({@link Caravans.Haul}) and takes them out of the world with it.
	 */
	static @Nullable PathfinderMob illager(ServerLevel level, Faction f, boolean carter, BlockPos at, float yaw) {
		Mob mob = (carter ? EntityTypes.PILLAGER : EntityTypes.VINDICATOR).create(level, EntitySpawnReason.EVENT);
		if (!(mob instanceof PathfinderMob crew)) {
			return null;
		}
		crew.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
		crew.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		if (crew instanceof Raider raider) {
			raider.setCanJoinRaid(false);
			raider.setPatrolLeader(false);
		}
		crew.setPersistenceRequired();
		crew.setCanPickUpLoot(false);
		crew.setHomeTo(at, 64);
		crew.addTag(CREW_TAG);
		((MobAccessor)crew).jaymoskingdoms$goals().addGoal(2, new Caravans.Haul(crew, carter));
		level.addFreshEntity(crew);
		return crew;
	}

	/** (0.7.490) A draft horse of {@code f}'s for a carter who is no folk or villager (an illager camp's pillager), and him in the saddle. */
	static @Nullable AbstractHorse horse(ServerLevel level, PathfinderMob rider, Faction f) {
		AbstractHorse horse = Equestrian.create(level, Breed.DRAFT, rider.blockPosition(), EntitySpawnReason.MOB_SUMMONED);
		if (horse == null) {
			return null;
		}
		horse.snapTo(rider.getX(), rider.getY(), rider.getZ(), rider.getYRot(), 0.0F);
		Cavalry.dress(level.getServer(), horse, f);
		horse.addTag(Cavalry.TAG);
		horse.addTag(Cavalry.OF + f.id());
		horse.addTag(Cavalry.LENT);
		level.addFreshEntity(horse);
		rider.startRiding(horse);
		return horse;
	}

	// ---- ogre holds -----------------------------------------------------------------------------------------------------

	/** (0.7.490) The ledger's key for when ogre hold {@code site} next sends its haul. */
	static String holdKey(int site) {
		return "ogre." + site;
	}

	/**
	 * (0.7.490) Each standing ogre hold whose time has come sends an ogre hauling a cart of hides, meat and hewn stone to the nearest
	 * greenskin camp in reach ({@link #OGRE_REACH}); the camp stores it, and sends him home with some of its food ({@link #arriveOgre}).
	 * The haul is the camp's caravan by name (its road, its robbery, its tribe's grudge), but timed and sent home by its hold.
	 */
	static void ogres(ServerLevel level, long now) {
		MinecraftServer server = level.getServer();
		for (OgreHolds.Site site : OgreHolds.standingHolds(server)) {
			Caravans.Ledger l = Caravans.ledger(server);
			String key = holdKey(site.id());
			Long due = l.next().get(key);
			if (due == null) {
				// (first sight of it: its first haul spread over its interval)
				next(server, key, now + Math.floorMod(site.id() * 7919L, OGRE_EVERY));
				continue;
			}
			if (now < due || l.caravans().values().stream().anyMatch(c -> c.hold() == site.id())) {
				continue;
			}
			if (peaceful(level)) {
				// ((0.7.490) on Peaceful no ogre stays in the world to pull a haul: none is sent)
				next(server, key, now + OGRE_RETRY);
				continue;
			}
			Faction camp = ogreCamp(server, site.at());
			if (camp == null) {
				next(server, key, now + OGRE_RETRY);
				continue;
			}
			Map<String, Integer> cart = new LinkedHashMap<>();
			int n = OGRE_HAUL_LEAST + level.getRandom().nextInt(OGRE_HAUL_MOST - OGRE_HAUL_LEAST + 1);
			for (int i = 0; i < n; i++) {
				cart.merge(Caravans.key(OGRE_GOODS[level.getRandom().nextInt(OGRE_GOODS.length)]), 1, Integer::sum);
			}
			l = Caravans.ledger(server);
			int id = l.nextId();
			Caravans.Caravan c = new Caravans.Caravan(id, camp.id(), camp.id(), false, site.at(), List.of(cart), 0, false, now,
				now + Caravans.STOP_TICKS / 2, HAND, false, site.id());
			Map<String, Caravans.Caravan> all = new HashMap<>(l.caravans());
			all.put(Integer.toString(id), c);
			Map<String, Long> next = new HashMap<>(l.next());
			next.put(key, now + OGRE_EVERY);
			Caravans.save(server, new Caravans.Ledger(all, next, l.pending(), id + 1));
			JaymosKingdoms.LOGGER.info("An ogre hold sends a cart of {} goods to {}", n, camp.name());
			if (level.isPositionEntityTicking(c.at())) {
				Caravans.appear(level, c);
			}
		}
	}

	private static void next(MinecraftServer server, String key, long when) {
		Caravans.Ledger l = Caravans.ledger(server);
		Map<String, Long> next = new HashMap<>(l.next());
		next.put(key, when);
		Caravans.save(server, new Caravans.Ledger(l.caravans(), next, l.pending(), l.nextId()));
	}

	/** (0.7.490) The standing greenskin camp nearest {@code at} in {@link #OGRE_REACH}, or null. */
	private static @Nullable Faction ogreCamp(MinecraftServer server, BlockPos at) {
		Faction best = null;
		double bestD = OGRE_REACH * OGRE_REACH;
		for (Faction g : Factions.all(server, FactionType.BARBARIAN)) {
			Camps.CampData data = Camps.data(server, g.id());
			if (g.fallen() || data == null || data.clearedAt() >= 0 || !Hordes.greenskin(server, g.id())) {
				continue;
			}
			double d = Raids.horizontal(at, g.seat());
			if (d < bestD) {
				best = g;
				bestD = d;
			}
		}
		return best;
	}

	/**
	 * (0.7.490) An ogre's haul at its end: at the camp ({@code camp}), the goods into its storehouse, and some of its food loaded for the
	 * hold (none when it is hungry itself); back at the hold, the food is eaten. Null when the round is done.
	 */
	static Caravans.@Nullable Caravan arriveOgre(ServerLevel level, Caravans.Caravan c, Faction camp, long now) {
		MinecraftServer server = level.getServer();
		if (c.back()) {
			return null;
		}
		for (Map<String, Integer> cart : c.carts()) {
			Caravans.deliver(level, camp, cart);
		}
		List<Map<String, Integer>> back = List.of();
		if (!HordeEconomy.hungry(server, camp.id()) && Storage.loaded(level, camp)) {
			back = Caravans.loadUp(level, camp, HordeEconomy::eats, Caravans.GREEN_KEEPS, c.load() * OGRE_FOOD_BACK / 100);
		}
		Map<String, Integer> food = new HashMap<>();
		for (Map<String, Integer> cart : back) {
			cart.forEach((k, v) -> food.merge(k, v, Integer::sum));
		}
		return c.turned(List.of(food), now + Caravans.STOP_TICKS);
	}

	// ---- players: escorting, trading, robbing ------------------------------------------------------------------------------

	/** Players near {@code lead} keep {@code c} company (called each time it is moved on while seen; (0.7.490) only while it is on the move, not while it stands loading). */
	static void escort(ServerLevel level, Caravans.Caravan c, net.minecraft.world.entity.Entity lead) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.isAlive() && p.distanceToSqr(lead) <= ESCORT_RANGE * ESCORT_RANGE) {
				ESCORTS.computeIfAbsent(c.id(), k -> new HashMap<>()).merge(p.getUUID(), Caravans.TICK, Integer::sum);
			}
		}
	}

	/** {@code c} has got to one end of its road: whoever kept it company all the way there is thanked (a settled owner's and partner's good name, a tip; a greenskin tribe's grudge eased). */
	static void reward(ServerLevel level, Caravans.Caravan c, Faction home, Faction lord) {
		MinecraftServer server = level.getServer();
		Map<UUID, Integer> who = ESCORTS.remove(c.id());
		if (who == null) {
			return;
		}
		String tribe = home.type() == FactionType.BARBARIAN ? Tribes.of(server, home.id()) : null;
		for (Map.Entry<UUID, Integer> e : who.entrySet()) {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null || e.getValue() < ESCORT_TICKS) {
				continue;
			}
			if (p.level() != level || p.isSpectator() || !p.isAlive() || p.distanceToSqr(Vec3.atCenterOf(c.at())) > ESCORT_RANGE * ESCORT_RANGE) {
				// ((0.7.490) he left it on the road: only one still with it at the end is thanked)
				continue;
			}
			if (tribe != null) {
				Tribes.add(server, p.getUUID(), tribe, -GREEN_EASE);
			} else if (!home.fallen() && (home.type().isRealm() || home.type() == FactionType.VILLAGE)) {
				// ((0.7.490) any settled owner, a fiefdom's own wagons to its overlord among them)
				Reputation.change(p, home, REP_ESCORT);
				if (lord.id() != home.id() && (lord.type().isRealm() || lord.type() == FactionType.VILLAGE)) {
					Reputation.change(p, lord, REP_ESCORT / 2);
				}
				long tip = spend(level, home, ESCORT_TIP);
				if (tip > 0L) {
					// (0.7.498) the tip is paid in the home town's coin (the treasury is counted in worth)
					Money.pay(p, home, Money.worth(tip));
				}
				p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.trade.escorted", home.name(), lord.name()).withStyle(ChatFormatting.GREEN));
			}
		}
	}

	/**
	 * A player with coin in hand (a coin, a stack or a purse) right-clicks a cart: he buys what it carries, a handful at a time, at a
	 * markup, paid in the cart owner's coin out of everything he carries (0.7.498; emeralds are a gem and buy nothing). True if that
	 * was what he did (or was refused); false to let it be looked at.
	 */
	static boolean deal(ServerLevel level, Wagon wagon, ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		if (!Coinage.isMoney(held)) {
			return false;
		}
		MinecraftServer server = level.getServer();
		Caravans.Caravan c = Caravans.get(server, wagon.caravan());
		Faction f = Factions.get(server, wagon.faction());
		if (c == null || f == null || wagon.slot() >= c.carts().size()) {
			return false;
		}
		if (f.type() == FactionType.BARBARIAN && Hordes.greenskin(server, f.id())) {
			// (they count in bent copper, and do not sell to a stranger)
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.trade.refused", f.name()).withStyle(ChatFormatting.GRAY), true);
			return true;
		}
		boolean settled = f.type().isRealm() || f.type() == FactionType.VILLAGE;
		if (settled && Reputation.get(player, f.id()) <= Reputation.HOSTILE) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.trade.refused", f.name()).withStyle(ChatFormatting.GRAY), true);
			return true;
		}
		Map<String, Integer> cart = new LinkedHashMap<>(c.carts().get(wagon.slot()));
		Map.Entry<String, Integer> pick = null;
		Item item = null;
		for (Map.Entry<String, Integer> e : cart.entrySet()) {
			item = Caravans.item(e.getKey());
			if (item != null && e.getValue() > 0) {
				pick = e;
				break;
			}
			item = null;
		}
		if (pick == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.trade.nothing", f.name()).withStyle(ChatFormatting.GRAY), true);
			return true;
		}
		long unit = Math.max(1L, (long)Ware.unit(new ItemStack(item)) * MARKUP / 100L);
		// (0.7.498) what he can pay, in whole emeralds' worth (the price notation), in the coin the cart's owner takes
		long have = player.hasInfiniteMaterials() ? Integer.MAX_VALUE : Money.count(player, f) / Money.UNIT;
		long n = Math.min(Math.min(pick.getValue(), BUY_MOST), have * COPPER_PER_EMERALD / unit);
		long cost = (n * unit + COPPER_PER_EMERALD - 1) / COPPER_PER_EMERALD;
		while (n > 0L && cost > have) {
			n--;
			cost = (n * unit + COPPER_PER_EMERALD - 1) / COPPER_PER_EMERALD;
		}
		if (n <= 0L) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.trade.dear", Money.text(f, Money.worth((unit + COPPER_PER_EMERALD - 1) / COPPER_PER_EMERALD)), new ItemStack(item).getHoverName())
				.withStyle(ChatFormatting.GRAY), true);
			return true;
		}
		if (!Money.charge(player, f, Money.worth(cost))) {
			// (rounding at the going rate for foreign coin can leave him a hair short)
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.trade.dear", Money.text(f, Money.worth(cost)), new ItemStack(item).getHoverName())
				.withStyle(ChatFormatting.GRAY), true);
			return true;
		}
		for (long left = n; left > 0L; ) {
			int stack = (int)Math.min(left, item.getDefaultMaxStackSize());
			ItemStack give = new ItemStack(item, stack);
			if (!player.getInventory().add(give)) {
				Block.popResource(level, player.blockPosition(), give);
			}
			left -= stack;
		}
		int remaining = (int)(pick.getValue() - n);
		if (remaining > 0) {
			cart.put(pick.getKey(), remaining);
		} else {
			cart.remove(pick.getKey());
		}
		List<Map<String, Integer>> carts = new ArrayList<>(c.carts());
		carts.set(wagon.slot(), cart);
		Caravans.put(server, c.carts(carts));
		wagon.setLoad(Caravans.look(cart));
		earn(level, f, cost);
		if (settled) {
			Reputation.change(player, f, REP_TRADE);
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.trade.bought", n, new ItemStack(item).getHoverName(), f.name(), Money.text(f, Money.worth(cost))).withStyle(ChatFormatting.GREEN), true);
		return true;
	}

	/**
	 * A player broke a trade cart open. A realm's, a town's or a poor man's handcart costs him its owner's good name (and half as much with the
	 * partner it was bound for or came from); a camp's is plundering, and the nearest settlement is glad of it.
	 */
	static void robbed(ServerLevel level, Wagon wagon, ServerPlayer player, Faction f, Caravans.Caravan c) {
		MinecraftServer server = level.getServer();
		Sort sort = sortOf(server, f);
		if (sort == null && f.type() != FactionType.KINGDOM && f.type() != FactionType.VILLAGE) {
			sort = Sort.HUMAN;
		}
		if (sort != null && sort.camp()) {
			Faction near = null;
			double bestD = PLUNDER_RANGE * PLUNDER_RANGE;
			for (Faction g : Factions.all(server)) {
				double d = Raids.horizontal(g.seat(), f.seat());
				if (!g.fallen() && (g.type() == FactionType.KINGDOM || g.type() == FactionType.VILLAGE) && d < bestD) {
					near = g;
					bestD = d;
				}
			}
			if (near != null) {
				Reputation.change(player, near, REP_PLUNDER);
			}
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.trade.plundered", f.name()).withStyle(ChatFormatting.GOLD));
			return;
		}
		boolean hand = wagon.kind() == Wagon.HANDCART;
		int by = hand ? REP_ROB_HAND : sort == Sort.TOWN ? REP_ROB_TOWN : REP_ROB_REALM;
		Reputation.change(player, f, by);
		Faction other = Factions.get(server, c.lord() == f.id() ? c.home() : c.lord());
		if (other != null && other.id() != f.id() && !other.fallen() && (other.type().isRealm() || other.type() == FactionType.VILLAGE)) {
			Reputation.change(player, other, by / 2);
		}
		player.sendSystemMessage(Component.translatable(hand ? "message.jaymoskingdoms.trade.robbed.hand" : "message.jaymoskingdoms.caravan.robbed", f.name())
			.withStyle(ChatFormatting.RED));
	}
}
