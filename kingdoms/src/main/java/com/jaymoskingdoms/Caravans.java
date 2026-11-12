package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.14) Caravans: every fiefdom sends its surplus to the kingdom it serves in horse-drawn storage carts, a few times
 * a day, and they come back with what it lacks. A mining fiefdom sends stone and ore and gets food back; a farming one
 * sends food and gets timber, coal and iron ({@link Focus}).
 *
 * <p><b>On the road.</b> A caravan is a record (saved on the Overworld) with its carts' loads and where it is. Where a
 * player is about, it's there to see: each cart a {@link Wagon} hitched to a horse with its carter (a {@link #CARTER})
 * in the saddle, the carts in a column on the road between the two seats (a {@link Roads King's Road}, else straight across), its guards (the fiefdom's, mounted
 * when the kingdom is prosperous) riding alongside. Away from every player it travels on in the record alone, at the
 * same pace, and appears again where it has got to when someone comes near. Its entities are tagged with the caravan
 * and the time it last appeared, so ones left in a chunk that unloaded are cleared away when it loads again.
 *
 * <p><b>Its loads.</b> When the fiefdom's storehouse is loaded, the carts take what it has beyond
 * {@link #FIEF_KEEPS} of each of its goods; when it isn't, they set out with a levy of its production ({@link #LEVY_BASE}
 * and {@link #LEVY_PER_VILLAGER}). The kingdom's storehouse takes the load on arrival, or keeps it waiting for it to
 * load ({@code pending}); the carts go back with what the kingdom has beyond {@link #KINGDOM_KEEPS}.
 *
 * <p><b>Robbing a caravan.</b> A cart struck {@link Wagon#CART_HEALTH} by a player breaks open and spills its load
 * where it stands; the fiefdom and its overlord think the worse of him ({@link #REP_ROB}), and its guards fall on him.
 * Carters and caravan guards killed cost good name as folk do, but are no loss to the fiefdom's roster.
 */
final class Caravans {
	/** A fiefdom's carter: drives one of its carts. Not a fighter. */
	public static final int CARTER = Folk.CARTER;

	/** How often caravans are moved on and seen to (ticks), and how often fiefdoms think of sending one. */
	static final int TICK = 20, SEND_CHECK = 200;
	/** A fiefdom sends a caravan this often at most (ticks: three a day), and tries again after this when it had nothing to send. */
	static final long CARAVAN_TICKS = 8000L, RETRY_TICKS = 2400L;
	/** The most carts a caravan has; a cart's load (items); the least worth sending. */
	static final int MOST_CARTS = 3, CART_LOAD = 9 * 64, LOAD_LEAST = 32;
	/** A fiefdom keeps this many of each of its goods; the kingdom this many of each it sends back. */
	static final int FIEF_KEEPS = 16, KINGDOM_KEEPS = 64;
	/** The carts go back with at most this share of what they brought (percent). */
	static final int RETURN_SHARE = 60;
	/** With its storehouse unloaded, a fiefdom sends this levy of its production (items), plus this per villager. */
	static final int LEVY_BASE = 96, LEVY_PER_VILLAGER = 4;
	/** Guards with it: this many, plus one for every this many carts. */
	static final int GUARDS_BASE = 1, CARTS_PER_GUARD = 2;
	/** Its pace on the road (blocks a tick, unseen; seen, the horses walk at {@link #DRIVE_SPEED} of their speed). */
	static final double ROAD_SPEED = 0.16, DRIVE_SPEED = 0.75;
	/** Its column: carts this far apart; it makes for a point this far ahead on its road; arrived this close. */
	static final double SPACING = 7.0, WAYPOINT = 18.0, ARRIVE = 9.0;
	/** It waits this long at either end (ticks), unloading and loading. */
	static final int STOP_TICKS = 160;
	/** No nearer its goal for this long (ticks) and it's helped over whatever is in its way: set this far on. */
	static final int STUCK_TICKS = 240;
	static final double HOP = 8.0;
	/** (0.7.490) A skiff stuck at a shore goes on this far (blocks) toward its goal in the record, so it doesn't come back where it stuck. */
	static final double SKIFF_SKIP = 32.0;
	/** (0.7.490) How far (blocks) a following skiff looks for water when its place in the column is land. */
	static final int SKIFF_SEARCH = 6;
	/** A caravan on the road longer than this (ticks) is given up (lost to something the world did). */
	static final long LONGEST = 24000L * 2;
	/** Reputation: a cart broken open by a player (with the fiefdom, and its overlord). */
	static final int REP_ROB = -60;
	/**
	 * (0.7.490) Greenskin carts: a camp keeps this much of each good back, sends at most this many items, in this many carts, with this many
	 * guards (and one more per {@link #CARTS_PER_GUARD} carts); it sends once in this long (ticks), and retries after the other; its tribe's
	 * camp it sends to is this near at most (blocks); a cart robbed raises the tribe's grudge by this much.
	 */
	static final int GREEN_KEEPS = 40, GREEN_LOAD_MOST = 192, GREEN_CARTS = 2, GREEN_GUARDS = 2, GREEN_ROB_GRUDGE = 12;
	static final long GREEN_TICKS = 24000L, GREEN_RETRY = 4800L;
	static final double GREEN_REACH = 900.0;
	/**
	 * (0.7.490) Haven carts: a haven sends once in this long (ticks), and retries after the other; one cart for every this many works it
	 * has (and one more), at most {@link #MOST_CARTS}; this many guards (its sworn, and one more per {@link #CARTS_PER_GUARD} carts);
	 * unseen, it sends a levy of this many goods and this many more for every work.
	 */
	static final long HAVEN_TICKS = 16000L, HAVEN_RETRY = 4800L;
	static final int HAVEN_WORKS_PER_CART = 3, HAVEN_GUARDS = 2, HAVEN_LEVY_BASE = 32, HAVEN_LEVY_PER_WORK = 24;

	static final String TAG = "jaymoskingdoms.caravan", TAG_OF = "jaymoskingdoms.caravan_of.";
	/** A caravan folk's task. */
	static final String TASK = "caravan";

	/** One caravan on the road: whose, to whom, which way ({@code back}), where it is, its carts' loads, its guards. */
	record Caravan(int id, int home, int lord, boolean back, BlockPos at, List<Map<String, Integer>> carts, int guards, boolean mounted,
		long started, long stopUntil, int rig, boolean trade, int hold) {
		static final Codec<Caravan> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("id").forGetter(Caravan::id),
				Codec.INT.fieldOf("home").forGetter(Caravan::home),
				Codec.INT.fieldOf("lord").forGetter(Caravan::lord),
				Codec.BOOL.fieldOf("back").forGetter(Caravan::back),
				BlockPos.CODEC.fieldOf("at").forGetter(Caravan::at),
				Codec.unboundedMap(Codec.STRING, Codec.INT).listOf().fieldOf("carts").forGetter(Caravan::carts),
				Codec.INT.fieldOf("guards").forGetter(Caravan::guards),
				Codec.BOOL.fieldOf("mounted").forGetter(Caravan::mounted),
				Codec.LONG.fieldOf("started").forGetter(Caravan::started),
				Codec.LONG.optionalFieldOf("stop_until", 0L).forGetter(Caravan::stopUntil),
				Codec.INT.optionalFieldOf("rig", TradeRoutes.CART).forGetter(Caravan::rig),
				Codec.BOOL.optionalFieldOf("trade", false).forGetter(Caravan::trade),
				// ((0.7.490) an ogre hold's haul: its site's id; -1 for everyone else's)
				Codec.INT.optionalFieldOf("hold", -1).forGetter(Caravan::hold))
			.apply(i, Caravan::new));

		Caravan at(BlockPos pos) {
			return new Caravan(this.id, this.home, this.lord, this.back, pos.immutable(), this.carts, this.guards, this.mounted, this.started, this.stopUntil, this.rig, this.trade, this.hold);
		}

		Caravan carts(List<Map<String, Integer>> carts) {
			return new Caravan(this.id, this.home, this.lord, this.back, this.at, List.copyOf(carts), this.guards, this.mounted, this.started, this.stopUntil, this.rig, this.trade, this.hold);
		}

		Caravan turned(List<Map<String, Integer>> carts, long stopUntil) {
			return new Caravan(this.id, this.home, this.lord, !this.back, this.at, List.copyOf(carts), this.guards, this.mounted, this.started, stopUntil, this.rig, this.trade, this.hold);
		}

		int load() {
			return this.carts.stream().mapToInt(c -> c.values().stream().mapToInt(Integer::intValue).sum()).sum();
		}
	}

	/** Every caravan, each fiefdom's next time to send one, goods waiting for a storehouse to load, the next id. */
	record Ledger(Map<String, Caravan> caravans, Map<String, Long> next, Map<String, Map<String, Integer>> pending, int nextId) {
		static final Ledger EMPTY = new Ledger(Map.of(), Map.of(), Map.of(), 1);
		static final Codec<Ledger> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(Codec.STRING, Caravan.CODEC).optionalFieldOf("caravans", Map.of()).forGetter(Ledger::caravans),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("next", Map.of()).forGetter(Ledger::next),
				Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.INT)).optionalFieldOf("pending", Map.of()).forGetter(Ledger::pending),
				Codec.INT.optionalFieldOf("next_id", 1).forGetter(Ledger::nextId))
			.apply(i, Ledger::new));
	}

	static final AttachmentType<Ledger> CARAVANS = AttachmentRegistry.create(JaymosKingdoms.id("caravans"),
		builder -> builder.persistent(Ledger.CODEC));

	/** A caravan as it is in the world right now: its carts (carter, horse, wagon), its guards, and the stamp on them. */
	private static final class Live {
		final String stamp;
		final List<UUID[]> carts = new ArrayList<>();
		final List<UUID> guards = new ArrayList<>();
		/** (0.7.490) How it's seen on the road just now: {@link TradeRoutes#CART}, {@link TradeRoutes#HAND} or {@link TradeRoutes#BOAT}. */
		int rig = TradeRoutes.CART;
		double best = Double.MAX_VALUE;
		long improvedAt;

		Live(String stamp) {
			this.stamp = stamp;
		}
	}

	private static final Map<Integer, Live> LIVE = new HashMap<>();
	/** Where each caravan folk is headed (set by the caravan, followed by its {@link Drive} goal). */
	private static final Map<UUID, Vec3> HEADING = new HashMap<>();

	private Caravans() {
	}

	static void register() {
		Folk.defineRole(new Folk.Role(CARTER, "carter", false, false, null, (folk, faction) -> {
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.LEAD));
			folk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.carter_of", faction.name()));
		}));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			long now = level.getGameTime();
			if (now % TICK == 0) {
				tick(level, now);
			}
			if (now % SEND_CHECK == 0) {
				send(level, now);
				flush(level);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> loaded(entity));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LIVE.clear();
			HEADING.clear();
			TradeRoutes.clear();
		});
	}

	// ---- the ledger --------------------------------------------------------------------------------------------------

	static Ledger ledger(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(CARAVANS, Ledger.EMPTY);
	}

	static void save(MinecraftServer server, Ledger ledger) {
		Factions.level(server).setAttached(CARAVANS, ledger);
	}

	static void put(MinecraftServer server, Caravan c) {
		Ledger l = ledger(server);
		Map<String, Caravan> all = new HashMap<>(l.caravans());
		all.put(Integer.toString(c.id()), c);
		save(server, new Ledger(all, l.next(), l.pending(), l.nextId()));
	}

	static void remove(MinecraftServer server, Caravan c, long nextSend) {
		// ((0.7.490) no one escorts it any more)
		TradeRoutes.forget(c.id());
		Ledger l = ledger(server);
		Map<String, Caravan> all = new HashMap<>(l.caravans());
		all.remove(Integer.toString(c.id()));
		Map<String, Long> next = new HashMap<>(l.next());
		// ((0.7.490) an ogre hold's haul is timed by its hold, not by the camp it hauls to)
		next.put(c.hold() >= 0 ? TradeRoutes.holdKey(c.hold()) : Integer.toString(c.home()), nextSend);
		save(server, new Ledger(all, next, l.pending(), l.nextId()));
	}

	static @Nullable Caravan get(MinecraftServer server, int id) {
		return ledger(server).caravans().get(Integer.toString(id));
	}

	static List<Caravan> all(MinecraftServer server) {
		return List.copyOf(ledger(server).caravans().values());
	}

	static String nameOf(MinecraftServer server, int faction) {
		Faction f = Factions.get(server, faction);
		return f == null ? "?" : f.name();
	}

	// ---- sending ------------------------------------------------------------------------------------------------------

	/** Each fiefdom whose time has come sends its surplus to its overlord. */
	private static void send(ServerLevel level, long now) {
		MinecraftServer server = level.getServer();
		if (!Royals.daytime(level)) {
			return;
		}
		Ledger l = ledger(server);
		for (Faction f : Factions.all(server, FactionType.FIEFDOM)) {
			if (l.caravans().values().stream().anyMatch(c -> c.home() == f.id()) || now < l.next().getOrDefault(Integer.toString(f.id()), 0L)) {
				continue;
			}
			start(level, f, false);
		}
		// ((0.7.490) and the greenskin camps send their surplus to the other camps of their tribe)
		for (Faction f : Factions.all(server, FactionType.BARBARIAN)) {
			if (f.fallen() || !Hordes.greenskin(server, f.id()) || l.caravans().values().stream().anyMatch(c -> c.home() == f.id() && c.hold() < 0)
				|| now < l.next().getOrDefault(Integer.toString(f.id()), 0L)) {
				continue;
			}
			startGreen(level, f, false);
		}
		// ((0.7.490) and everyone else: kingdoms, villages and towns, bandit, illager and pirate camps; havens send their own, below)
		TradeRoutes.send(level, now);
		// ((0.7.490) and the havens, which only trade, send their output to the settlement that buys it best)
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || !tradingHaven(server, f) || l.caravans().values().stream().anyMatch(c -> c.home() == f.id())
				|| now < l.next().getOrDefault(Integer.toString(f.id()), 0L)) {
				continue;
			}
			startHaven(level, f, false);
		}
	}

	/** (0.7.490) {@code f} is a haven that trades on its own account (a fiefdom of an overlord sends to it, as ever). */
	static boolean tradingHaven(MinecraftServer server, Faction f) {
		return Havens.is(server, f) && Relations.overlord(server, f) == null;
	}

	/**
	 * (0.7.490) Haven {@code f} sends a cart or three of its output (and its wares) to the settlement that buys it best, guarded by its
	 * sworn fighters; the more it works, the more carts. {@code force}: whatever the time and the load.
	 */
	static @Nullable Caravan startHaven(ServerLevel level, Faction f, boolean force) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		Faction to = f.fallen() || !Economy.atPeace(server, f) ? null : HavenGrowth.partner(server, f);
		int works = HavenGrowth.works(server, f);
		if (to == null || works == 0 && !force) {
			later(server, f, now + HAVEN_RETRY);
			return null;
		}
		int most = Math.min(MOST_CARTS, 1 + works / HAVEN_WORKS_PER_CART) * CART_LOAD;
		List<Map<String, Integer>> carts = Storage.loaded(level, f) ? loadUp(level, f, HavenGrowth::trades, HavenGrowth::keeps, most) : havenLevy(level, f, works);
		int load = carts.stream().mapToInt(c -> c.values().stream().mapToInt(Integer::intValue).sum()).sum();
		if (load < LOAD_LEAST && !force) {
			for (Map<String, Integer> cart : carts) {
				deliver(level, f, cart);
			}
			later(server, f, now + HAVEN_RETRY);
			return null;
		}
		if (carts.isEmpty()) {
			carts = List.of(Map.of());
		}
		Ledger l = ledger(server);
		int id = l.nextId();
		boolean mounted = f.type().isRealm() && Wealth.atLeast(server, f, Wealth.Tier.PROSPEROUS);
		Caravan c = new Caravan(id, f.id(), to.id(), false, start(f), carts, HAVEN_GUARDS + carts.size() / CARTS_PER_GUARD, mounted, now, now + STOP_TICKS / 2, TradeRoutes.CART, false, -1);
		Map<String, Caravan> all = new HashMap<>(l.caravans());
		all.put(Integer.toString(id), c);
		Map<String, Long> next = new HashMap<>(l.next());
		next.put(Integer.toString(f.id()), now + HAVEN_TICKS);
		save(server, new Ledger(all, next, l.pending(), id + 1));
		JaymosKingdoms.LOGGER.info("{} sends {} carts of its output ({} goods) to {}", f.name(), carts.size(), load, to.name());
		if (level.isPositionEntityTicking(c.at())) {
			appear(level, c);
		}
		return c;
	}

	/** (0.7.490) A levy of a haven's works' goods, sent while its storehouse is out of reach (nothing is taken from it). */
	private static List<Map<String, Integer>> havenLevy(ServerLevel level, Faction f, int works) {
		List<Item> goods = HavenGrowth.levyGoods(level.getServer(), f);
		Map<String, Integer> all = new LinkedHashMap<>();
		if (!goods.isEmpty()) {
			for (int i = 0; i < HAVEN_LEVY_BASE + works * HAVEN_LEVY_PER_WORK; i++) {
				all.merge(key(goods.get(level.getRandom().nextInt(goods.size()))), 1, Integer::sum);
			}
		}
		return split(all);
	}

	/**
	 * (0.7.490) A haven's cart at the settlement that buys: the goods handed over and paid for in emeralds (a realm's treasury or a
	 * village's storehouse pays as much as it holds, into the haven's own), the carts loaded again for home with what the buyer has
	 * in plenty that the haven builds with (timber, rock, ore, bread).
	 */
	private static Caravan arriveHaven(ServerLevel level, Caravan c, Faction home, Faction buyer, long now) {
		MinecraftServer server = level.getServer();
		long worth = 0L;
		for (Map<String, Integer> cart : c.carts()) {
			deliver(level, buyer, cart);
			for (Map.Entry<String, Integer> e : cart.entrySet()) {
				Item item = item(e.getKey());
				worth += item == null ? 0L : HavenGrowth.worth(item, e.getValue());
			}
		}
		long paid = 0L;
		// (0.7.490) a realm pays from its treasury, a village from its storehouse (TradeRoutes.spend / earn)
		paid = TradeRoutes.spend(level, buyer, buyer.type().isRealm() ? worth : Math.min(worth, 64L * 8));
		TradeRoutes.earn(level, home, paid);
		List<Map<String, Integer>> back = List.of();
		if (Storage.loaded(level, buyer)) {
			back = loadUp(level, buyer, x -> Storage.isFood(x) || x.is(ItemTags.LOGS) || x.is(Items.COAL) || x.is(Items.IRON_INGOT) || x.is(Items.COBBLESTONE),
				KINGDOM_KEEPS, Math.min(c.carts().size() * CART_LOAD, (int)Math.min((long)Integer.MAX_VALUE, c.load() * (long)RETURN_SHARE / 100)));
		}
		List<Map<String, Integer>> carts = new ArrayList<>(back);
		while (carts.size() < c.carts().size()) {
			carts.add(Map.of());
		}
		while (carts.size() > c.carts().size()) {
			Map<String, Integer> extra = carts.removeLast();
			Map<String, Integer> merged = new HashMap<>(carts.getLast());
			extra.forEach((k, v) -> merged.merge(k, v, Integer::sum));
			carts.set(carts.size() - 1, merged);
		}
		return c.turned(carts, now + STOP_TICKS);
	}

	/** (0.7.490) How long after {@code c} is done its home waits to send another (ticks). */
	static long gap(MinecraftServer server, Caravan c) {
		return c.hold() >= 0 ? TradeRoutes.OGRE_EVERY : c.trade() ? TradeRoutes.every(server, c.home()) : CARAVAN_TICKS;
	}

	/** The camp of {@code f}'s tribe its cart goes to: a hungry one first, else the nearest standing (within {@link #GREEN_REACH}); null if it's alone. */
	static @Nullable Faction greenGoal(MinecraftServer server, Faction f) {
		String tribe = Tribes.of(server, f.id());
		if (tribe == null) {
			return null;
		}
		Faction best = null;
		double bestD = GREEN_REACH * GREEN_REACH;
		boolean bestHungry = false;
		for (Faction g : Tribes.camps(server, tribe)) {
			Camps.CampData data = Camps.data(server, g.id());
			double d = Raids.horizontal(g.seat(), f.seat());
			if (g.id() == f.id() || g.fallen() || data == null || data.clearedAt() >= 0 || d > GREEN_REACH * GREEN_REACH) {
				continue;
			}
			boolean hungry = HordeEconomy.hungry(server, g.id());
			if (hungry && !bestHungry || hungry == bestHungry && d < bestD) {
				best = g;
				bestD = d;
				bestHungry = hungry;
			}
		}
		return best;
	}

	/** (0.7.490) The goods a greenskin camp of {@code orc}'s kind trades in. */
	private static Predicate<ItemStack> greenGoods(boolean orc, boolean food) {
		return x -> food ? HordeEconomy.eats(x) : GreenMarket.goods(orc).stream().anyMatch(g -> x.is(g.item()));
	}

	/**
	 * (0.7.490) Greenskin camp {@code f} sends a cart or two of its surplus to a camp of its tribe (food, if that camp is hungry),
	 * along their road, guarded by its own folk; not while it is hungry itself. {@code force}: whatever the time and the load.
	 */
	static @Nullable Caravan startGreen(ServerLevel level, Faction f, boolean force) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		Camps.CampData data = Camps.data(server, f.id());
		Faction to = data == null || data.clearedAt() >= 0 || f.fallen() ? null : greenGoal(server, f);
		if (to == null || !Storage.loaded(level, f) || HordeEconomy.hungry(server, f.id())) {
			later(server, f, now + GREEN_RETRY);
			return null;
		}
		List<Map<String, Integer>> carts = loadUp(level, f, greenGoods(data.orc(), HordeEconomy.hungry(server, to.id())), GREEN_KEEPS,
			GREEN_LOAD_MOST * TradeRoutes.ogreLoad(server, data));
		int load = carts.stream().mapToInt(c -> c.values().stream().mapToInt(Integer::intValue).sum()).sum();
		if (load < LOAD_LEAST && !force || carts.size() > GREEN_CARTS) {
			// (nothing worth the road, or more than it can carry: back what was taken, and wait)
			for (Map<String, Integer> cart : carts) {
				deliver(level, f, cart);
			}
			later(server, f, now + GREEN_RETRY);
			return null;
		}
		if (carts.isEmpty()) {
			carts = List.of(Map.of());
		}
		Ledger l = ledger(server);
		int id = l.nextId();
		Caravan c = new Caravan(id, f.id(), to.id(), false, start(f), carts, GREEN_GUARDS + carts.size() / CARTS_PER_GUARD, false, now, now + STOP_TICKS / 2, TradeRoutes.CART, false, -1);
		Map<String, Caravan> all = new HashMap<>(l.caravans());
		all.put(Integer.toString(id), c);
		Map<String, Long> next = new HashMap<>(l.next());
		next.put(Integer.toString(f.id()), now + GREEN_TICKS);
		save(server, new Ledger(all, next, l.pending(), id + 1));
		JaymosKingdoms.LOGGER.info("{} sends a cart of {} goods to {}", f.name(), load, to.name());
		if (level.isPositionEntityTicking(c.at())) {
			appear(level, c);
		}
		return c;
	}

	/** (0.7.490) What a cart's {@code cargo} is worth in bent ingots at a camp's prices (a lot of each good at its base price). */
	private static long cargoWorth(Map<String, Integer> cargo, boolean orc) {
		long worth = 0L;
		for (Map.Entry<String, Integer> e : cargo.entrySet()) {
			Item item = item(e.getKey());
			for (GreenMarket.Good g : GreenMarket.goods(orc)) {
				if (item != null && g.item() == item) {
					worth += (long)e.getValue() * g.base() / g.lot();
				}
			}
		}
		return worth;
	}

	/** {@code f} sends a caravan now (if it can: an overlord standing, at peace, goods to send). {@code force}: whatever the time. */
	static @Nullable Caravan start(ServerLevel level, Faction f, boolean force) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		Faction lord = Relations.overlord(server, f);
		Focus.Kind focus = Focus.of(server, f);
		if (f.fallen() || lord == null || lord.fallen() || focus == null || !Economy.atPeace(server, f) || Economy.atWar(server, lord)) {
			later(server, f, now + RETRY_TICKS);
			return null;
		}
		List<Map<String, Integer>> carts;
		if (Storage.loaded(level, f)) {
			carts = loadUp(level, f, x -> Focus.goods(focus, x), FIEF_KEEPS, MOST_CARTS * CART_LOAD);
		} else {
			carts = levy(level, f, focus);
		}
		int load = carts.stream().mapToInt(c -> c.values().stream().mapToInt(Integer::intValue).sum()).sum();
		if (load < LOAD_LEAST && !force) {
			// (nothing worth the road: back what was taken, and wait)
			for (Map<String, Integer> cart : carts) {
				deliver(level, f, cart);
			}
			later(server, f, now + RETRY_TICKS);
			return null;
		}
		if (carts.isEmpty()) {
			carts = List.of(Map.of());
		}
		Ledger l = ledger(server);
		int id = l.nextId();
		int guards = GUARDS_BASE + carts.size() / CARTS_PER_GUARD;
		boolean mounted = Wealth.atLeast(server, lord, Wealth.Tier.PROSPEROUS);
		Caravan c = new Caravan(id, f.id(), lord.id(), false, start(f), carts, guards, mounted, now, now + STOP_TICKS / 2, TradeRoutes.CART, false, -1);
		Map<String, Caravan> all = new HashMap<>(l.caravans());
		all.put(Integer.toString(id), c);
		Map<String, Long> next = new HashMap<>(l.next());
		next.put(Integer.toString(f.id()), now + CARAVAN_TICKS);
		save(server, new Ledger(all, next, l.pending(), id + 1));
		JaymosKingdoms.LOGGER.info("{} sends a caravan of {} carts ({} goods) to {}", f.name(), carts.size(), load, lord.name());
		if (level.isPositionEntityTicking(c.at())) {
			appear(level, c);
		}
		return c;
	}

	static void later(MinecraftServer server, Faction f, long when) {
		Ledger l = ledger(server);
		Map<String, Long> next = new HashMap<>(l.next());
		next.put(Integer.toString(f.id()), when);
		save(server, new Ledger(l.caravans(), next, l.pending(), l.nextId()));
	}

	/**
	 * (0.7.490) {@code f} sends a trade caravan of {@code carts} to {@code to} ({@link TradeRoutes}), in {@code rig}; it may send again at
	 * {@code nextSend}. It appears at once if anyone is near enough to see.
	 */
	static Caravan launch(ServerLevel level, Faction f, Faction to, List<Map<String, Integer>> carts, int guards, boolean mounted, int rig, long nextSend) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		Ledger l = ledger(server);
		int id = l.nextId();
		Caravan c = new Caravan(id, f.id(), to.id(), false, rig == TradeRoutes.BOAT ? TradeRoutes.quay(server, f) : start(f), carts, guards, mounted, now,
			now + STOP_TICKS / 2, rig, true, -1);
		Map<String, Caravan> all = new HashMap<>(l.caravans());
		all.put(Integer.toString(id), c);
		Map<String, Long> next = new HashMap<>(l.next());
		next.put(Integer.toString(f.id()), nextSend);
		save(server, new Ledger(all, next, l.pending(), id + 1));
		if (level.isPositionEntityTicking(c.at())) {
			appear(level, c);
		}
		return c;
	}

	/** (0.7.490) Where {@code c} is headed: the far end's door, or home's; a skiff makes for its cove's mooring ({@link TradeRoutes#quay}). */
	static BlockPos goal(MinecraftServer server, Caravan c, Faction home, Faction lord) {
		if (c.hold() >= 0 && c.back()) {
			// ((0.7.490) an ogre hold's haul goes home to its hold)
			BlockPos hold = OgreHolds.holdAt(server, c.hold());
			if (hold != null) {
				return hold;
			}
		}
		Faction end = c.back() ? home : lord;
		return c.trade() && c.rig() == TradeRoutes.BOAT ? TradeRoutes.quay(server, end) : start(end);
	}

	/** Where a faction's caravans load and unload: its storehouse door, else its seat. */
	static BlockPos start(Faction f) {
		return f.holdings().depot().orElse(f.seat());
	}

	/** Takes from {@code f}'s storage what it has of {@code what} beyond {@code keep} of each, at most {@code most}, a cart's load at a time. */
	static List<Map<String, Integer>> loadUp(ServerLevel level, Faction f, Predicate<ItemStack> what, int keep, int most) {
		return loadUp(level, f, what, x -> keep, most, CART_LOAD);
	}

	/** (0.7.490) {@link #loadUp} into carts of {@code cap} items each (a handcart takes less than a wagon). */
	static List<Map<String, Integer>> loadUp(ServerLevel level, Faction f, Predicate<ItemStack> what, int keep, int most, int cap) {
		return loadUp(level, f, what, x -> keep, most, cap);
	}

	/** (0.7.490) The same, keeping {@code keeps} of each good (a haven keeps more of what it builds with). */
	private static List<Map<String, Integer>> loadUp(ServerLevel level, Faction f, Predicate<ItemStack> what, java.util.function.ToIntFunction<ItemStack> keeps,
		int most) {
		return loadUp(level, f, what, keeps, most, CART_LOAD);
	}

	/** (0.7.490) The same, in carts of {@code cap} items each. */
	private static List<Map<String, Integer>> loadUp(ServerLevel level, Faction f, Predicate<ItemStack> what, java.util.function.ToIntFunction<ItemStack> keeps,
		int most, int cap) {
		List<Map<String, Integer>> carts = new ArrayList<>();
		Map<String, Integer> cart = new LinkedHashMap<>();
		int inCart = 0, total = 0;
		for (Map.Entry<Item, Integer> e : Storage.totals(level, f).entrySet()) {
			ItemStack sample = new ItemStack(e.getKey());
			int spare = e.getValue() - keeps.applyAsInt(sample);
			if (spare <= 0 || !what.test(sample)) {
				continue;
			}
			while (spare > 0 && total < most) {
				int take = Math.min(64, Math.min(spare, Math.min(cap - inCart, most - total)));
				ItemStack taken = Storage.take(level, f, e.getKey(), take);
				if (taken.isEmpty()) {
					break;
				}
				cart.merge(key(e.getKey()), taken.getCount(), Integer::sum);
				inCart += taken.getCount();
				total += taken.getCount();
				spare -= taken.getCount();
				if (inCart >= cap) {
					carts.add(cart);
					cart = new LinkedHashMap<>();
					inCart = 0;
				}
				if (taken.getCount() < take) {
					break;
				}
			}
		}
		if (!cart.isEmpty()) {
			carts.add(cart);
		}
		return carts;
	}

	/** A levy of {@code f}'s production, sent while its storehouse is out of reach (nothing is taken from it). */
	private static List<Map<String, Integer>> levy(ServerLevel level, Faction f, Focus.Kind focus) {
		int n = LEVY_BASE + f.holdings().population() * LEVY_PER_VILLAGER;
		Item[] goods = focus == Focus.Kind.MINING
			? new Item[] {Items.COBBLESTONE, Items.COBBLESTONE, Items.COBBLESTONE, Items.STONE, Items.COAL, Items.RAW_IRON, Items.RAW_COPPER, Items.ANDESITE}
			: new Item[] {Items.WHEAT, Items.WHEAT, Items.BREAD, Items.POTATO, Items.CARROT, Items.BEETROOT, Items.BEEF, Items.MUTTON, Items.APPLE};
		Map<String, Integer> all = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) {
			all.merge(key(goods[level.getRandom().nextInt(goods.length)]), 1, Integer::sum);
		}
		return split(all);
	}

	/** Goods shared out in carts of {@link #CART_LOAD}. */
	private static List<Map<String, Integer>> split(Map<String, Integer> goods) {
		List<Map<String, Integer>> carts = new ArrayList<>();
		Map<String, Integer> cart = new LinkedHashMap<>();
		int in = 0;
		for (Map.Entry<String, Integer> e : goods.entrySet()) {
			int left = e.getValue();
			while (left > 0 && carts.size() < MOST_CARTS) {
				int take = Math.min(left, CART_LOAD - in);
				cart.merge(e.getKey(), take, Integer::sum);
				in += take;
				left -= take;
				if (in >= CART_LOAD) {
					carts.add(cart);
					cart = new LinkedHashMap<>();
					in = 0;
				}
			}
		}
		if (!cart.isEmpty() && carts.size() < MOST_CARTS) {
			carts.add(cart);
		}
		return carts;
	}

	static String key(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	static @Nullable Item item(String key) {
		Identifier id = Identifier.tryParse(key);
		Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
		return item == null || item == Items.AIR ? null : item;
	}

	// ---- delivering -------------------------------------------------------------------------------------------------

	/** Into {@code f}'s storage if it's loaded (what doesn't fit is lost), else kept waiting for it. */
	static void deliver(ServerLevel level, Faction f, Map<String, Integer> goods) {
		if (goods.isEmpty()) {
			return;
		}
		if (Storage.loaded(level, f)) {
			for (Map.Entry<String, Integer> e : goods.entrySet()) {
				Item item = item(e.getKey());
				if (item != null) {
					Storage.put(level, f, item, e.getValue());
				}
			}
			return;
		}
		MinecraftServer server = level.getServer();
		Ledger l = ledger(server);
		Map<String, Map<String, Integer>> pending = new HashMap<>(l.pending());
		Map<String, Integer> mine = new HashMap<>(pending.getOrDefault(Integer.toString(f.id()), Map.of()));
		goods.forEach((k, v) -> mine.merge(k, v, Integer::sum));
		pending.put(Integer.toString(f.id()), Map.copyOf(mine));
		save(server, new Ledger(l.caravans(), l.next(), pending, l.nextId()));
	}

	/** Goods left waiting go in once their storehouse is loaded. */
	private static void flush(ServerLevel level) {
		MinecraftServer server = level.getServer();
		Ledger l = ledger(server);
		if (l.pending().isEmpty()) {
			return;
		}
		Map<String, Map<String, Integer>> pending = new HashMap<>(l.pending());
		boolean changed = false;
		for (Map.Entry<String, Map<String, Integer>> e : l.pending().entrySet()) {
			Faction f = Factions.get(server, Integer.parseInt(e.getKey()));
			if (f == null || f.fallen()) {
				pending.remove(e.getKey());
				changed = true;
			} else if (Storage.loaded(level, f)) {
				pending.remove(e.getKey());
				changed = true;
				save(server, new Ledger(l.caravans(), l.next(), Map.copyOf(pending), l.nextId()));
				deliver(level, f, e.getValue());
				l = ledger(server);
			}
		}
		if (changed) {
			l = ledger(server);
			save(server, new Ledger(l.caravans(), l.next(), Map.copyOf(pending), l.nextId()));
		}
	}

	/** At the far end: its load handed over; the carts loaded again for home (what the fiefdom lacks), and turned round. */
	private static @Nullable Caravan arrive(ServerLevel level, Caravan c, long now) {
		MinecraftServer server = level.getServer();
		Faction home = Factions.get(server, c.home());
		Faction lord = Factions.get(server, c.lord());
		if (c.hold() >= 0 && home != null) {
			// ((0.7.490) an ogre hold's haul at the camp it trades with, or back at its hold)
			return TradeRoutes.arriveOgre(level, c, home, now);
		}
		if (c.trade() && home != null && lord != null) {
			// ((0.7.490) a settlement's trade: villages, towns, camps, coves and kingdoms)
			return TradeRoutes.arrive(level, c, home, lord, now);
		}
		if (home != null && lord != null) {
			TradeRoutes.reward(level, c, home, lord);
		}
		if (!c.back() && home != null && lord != null && home.type() == FactionType.BARBARIAN) {
			return arriveGreen(level, c, home, lord, now);
		}
		if (!c.back() && home != null && lord != null && home.type() != FactionType.BARBARIAN && tradingHaven(server, home)) {
			// ((0.7.490) a haven's cart at the buyer)
			return arriveHaven(level, c, home, lord, now);
		}
		if (!c.back()) {
			int load = c.load();
			if (lord != null) {
				for (Map<String, Integer> cart : c.carts()) {
					deliver(level, lord, cart);
				}
				Treasuries.dues(server, lord, load);
			}
			List<Map<String, Integer>> back = List.of();
			Focus.Kind focus = home == null ? null : Focus.of(server, home);
			if (lord != null && focus != null && Storage.loaded(level, lord)) {
				Predicate<ItemStack> wants = focus == Focus.Kind.MINING ? Storage::isFood
					: x -> x.is(ItemTags.LOGS) || x.is(Items.COAL) || x.is(Items.IRON_INGOT) || x.is(Items.COBBLESTONE);
				back = loadUp(level, lord, wants, KINGDOM_KEEPS, Math.min(c.carts().size() * CART_LOAD, Math.max(0, load * RETURN_SHARE / 100)));
			}
			// (the same carts go home, loaded or not)
			List<Map<String, Integer>> carts = new ArrayList<>(back);
			while (carts.size() < c.carts().size()) {
				carts.add(Map.of());
			}
			while (carts.size() > c.carts().size()) {
				Map<String, Integer> extra = carts.removeLast();
				Map<String, Integer> merged = new HashMap<>(carts.getLast());
				extra.forEach((k, v) -> merged.merge(k, v, Integer::sum));
				carts.set(carts.size() - 1, merged);
			}
			return c.turned(carts, now + STOP_TICKS);
		}
		if (home != null) {
			for (Map<String, Integer> cart : c.carts()) {
				deliver(level, home, cart);
			}
		}
		return null;
	}

	/**
	 * (0.7.490) A greenskin cart at its tribe's other camp: the goods handed over, the camp paying for them out of its hoard into the
	 * sender's (as much as it holds), and the carts loaded again for home with some of what that camp has in plenty.
	 */
	private static Caravan arriveGreen(ServerLevel level, Caravan c, Faction home, Faction lord, long now) {
		MinecraftServer server = level.getServer();
		Camps.CampData homeData = Camps.data(server, home.id());
		boolean homeOrc = homeData != null && homeData.orc();
		Camps.CampData lordData = Camps.data(server, lord.id());
		long worth = 0L;
		for (Map<String, Integer> cart : c.carts()) {
			deliver(level, lord, cart);
			worth += cargoWorth(cart, homeOrc);
		}
		long paid = Math.min(worth, HordeEconomy.hoard(server, lord.id()));
		if (paid > 0L && HordeEconomy.spend(server, lord.id(), paid)) {
			HordeEconomy.deposit(server, home.id(), paid);
		}
		List<Map<String, Integer>> back = List.of();
		if (lordData != null && Storage.loaded(level, lord)) {
			back = loadUp(level, lord, greenGoods(lordData.orc(), HordeEconomy.hungry(server, home.id())), GREEN_KEEPS,
				Math.min(c.carts().size() * CART_LOAD, GREEN_LOAD_MOST * RETURN_SHARE / 100));
		}
		List<Map<String, Integer>> carts = new ArrayList<>(back);
		while (carts.size() < c.carts().size()) {
			carts.add(Map.of());
		}
		while (carts.size() > c.carts().size()) {
			Map<String, Integer> extra = carts.removeLast();
			Map<String, Integer> merged = new HashMap<>(carts.getLast());
			extra.forEach((k, v) -> merged.merge(k, v, Integer::sum));
			carts.set(carts.size() - 1, merged);
		}
		return c.turned(carts, now + STOP_TICKS);
	}

	// ---- on the road --------------------------------------------------------------------------------------------------

	/** Every caravan moved on: in the world where it's seen, in the record where it isn't. */
	private static void tick(ServerLevel level, long now) {
		MinecraftServer server = level.getServer();
		for (Caravan c : all(server)) {
			Faction home = Factions.get(server, c.home());
			Faction lord = Factions.get(server, c.lord());
			if (home == null || lord == null || home.fallen() || lord.fallen() || now - c.started() > LONGEST) {
				// (its fiefdom or its kingdom is gone: the caravan breaks up)
				vanish(level, c);
				remove(server, c, now + gap(server, c));
				continue;
			}
			BlockPos goal = goal(server, c, home, lord);
			Live live = LIVE.get(c.id());
			// ((0.7.490) a monster-crewed caravan on Peaceful: its crew can't stay in the world, so it stays out of sight)
			boolean unseen = TradeRoutes.unseenCrew(level, c);
			if (live != null && unseen) {
				vanish(level, c);
				live = null;
			}
			if (live != null) {
				drive(level, c, live, goal, now);
				continue;
			}
			// unseen: on along the road, or round at its end
			if (!unseen && level.isPositionEntityTicking(c.at())) {
				appear(level, c);
				continue;
			}
			if (now < c.stopUntil()) {
				continue;
			}
			Vec3 at = Vec3.atBottomCenterOf(c.at());
			Vec3 to = Vec3.atBottomCenterOf(goal);
			double d = at.distanceTo(to);
			if (d <= ARRIVE) {
				reached(level, c, now);
				continue;
			}
			// ((0.7.490) a handcart, or an ogre's cart, goes slower than a horse: TradeRoutes.pace)
			double step = ROAD_SPEED * TICK * TradeRoutes.pace(level, c);
			Vec3 road = Roads.ahead(level, at, goal, step);
			Vec3 next = road != null ? road : at.add(to.subtract(at).scale(Math.min(1.0, step / d)));
			put(server, c.at(BlockPos.containing(next)));
		}
	}

	/** It's reached its goal: on to the far end's business, or home and done. */
	private static void reached(ServerLevel level, Caravan c, long now) {
		MinecraftServer server = level.getServer();
		Caravan turned = arrive(level, c, now);
		if (turned == null) {
			// home: the carters see to their horses, the guards go back to their posts
			vanish(level, c);
			remove(server, c, now + gap(server, c));
			return;
		}
		put(server, turned);
		Live live = LIVE.get(c.id());
		if (live != null) {
			live.best = Double.MAX_VALUE;
			live.improvedAt = now;
			restyle(level, turned, live);
		}
	}

	/** Seen: the carts in a column making for their goal, its guards alongside. */
	private static void drive(ServerLevel level, Caravan c, Live live, BlockPos goal, long now) {
		MinecraftServer server = level.getServer();
		// its carts, those still whole
		List<Entity[]> carts = new ArrayList<>();
		boolean any = false;
		for (UUID[] ids : live.carts) {
			Entity carter = level.getEntity(ids[0]), horse = level.getEntity(ids[1]), wagon = level.getEntity(ids[2]);
			any |= carter != null || horse != null || wagon != null;
			if (wagon instanceof Wagon w && !w.isRemoved() && (carter == null || !carter.isAlive() || horse == null || !horse.isAlive())) {
				// (its carter or its horse fell: the cart is left where it stands, broken open)
				spill(level, w);
				continue;
			}
			if (carter != null && carter.isAlive() && horse != null && horse.isAlive() && wagon != null && !wagon.isRemoved()) {
				if (horse != carter) {
					remount(carter, horse);
				}
				if (horse != wagon && !level.isPositionEntityTicking(wagon.blockPosition()) && level.isPositionEntityTicking(horse.blockPosition())) {
					((Wagon)wagon).trail(horse);
				}
				carts.add(new Entity[] {carter, horse, wagon});
			}
		}
		if (!any) {
			// (all gone from the world: unloaded with their chunk; it goes on unseen)
			LIVE.remove(c.id());
			return;
		}
		if (carts.isEmpty()) {
			// every cart lost
			lost(level, c);
			return;
		}
		Entity lead = carts.getFirst()[1];
		if (!level.isPositionEntityTicking(lead.blockPosition())) {
			// out of sight: it goes on in the record from here
			put(server, c.at(lead.blockPosition()));
			vanish(level, c);
			return;
		}
		put(server, c.at(lead.blockPosition()));
		// ((0.7.294) folk, or MCA guards)
		List<PathfinderMob> guards = new ArrayList<>();
		for (UUID id : live.guards) {
			// ((0.7.490) or an illager camp's own illagers: TradeRoutes.crew)
			if (level.getEntity(id) instanceof PathfinderMob g && (g instanceof Worker || TradeRoutes.crew(g)) && g.isAlive()) {
				guards.add(g);
			}
		}
		for (PathfinderMob g : guards) {
			// ((0.7.453) not one off his horse to tend someone downed: he climbs back on himself)
			if (c.mounted() && !g.isPassenger() && !Downed.helping(g)) {
				AbstractHorse spare = level.getEntitiesOfClass(AbstractHorse.class, g.getBoundingBox().inflate(12.0),
					h -> h.isAlive() && !h.isVehicle() && h.entityTags().contains(live.stamp) && !Downed.waiting(h)).stream().findFirst().orElse(null);
				if (spare != null) {
					remount(g, spare);
				}
			}
		}
		Vec3 to = Vec3.atBottomCenterOf(goal);
		double d = Math.sqrt(lead.distanceToSqr(to.x, lead.getY(), to.z));
		if (now < c.stopUntil()) {
			// unloading, loading: they stand
			for (Entity[] cart : carts) {
				if (live.rig == TradeRoutes.HAND) {
					// (a handcart's puller holds where he is)
					HEADING.put(cart[0].getUUID(), cart[0].position());
				} else {
					HEADING.remove(cart[0].getUUID());
				}
				if (live.rig == TradeRoutes.BOAT && cart[2] instanceof Wagon skiff) {
					skiff.sailTo(null);
				}
			}
			for (PathfinderMob g : guards) {
				HEADING.remove(g.getUUID());
			}
			if (!carts.isEmpty()) {
				Entity w = carts.get(level.getRandom().nextInt(carts.size()))[2];
				WorldSounds.cart(level, carts.get(0)[2].getId(), w);
			}
			return;
		}
		// ((0.7.490) any player keeping it company on the road is an escort: only while it is moving, not while it stands loading)
		TradeRoutes.escort(level, c, lead);
		if (d <= ARRIVE) {
			reached(level, c, now);
			return;
		}
		if (d < live.best - 0.5) {
			live.best = d;
			live.improvedAt = now;
		} else if (now - live.improvedAt > STUCK_TICKS) {
			if (live.rig == TradeRoutes.BOAT) {
				// (0.7.490) a skiff with no water ahead sails on out of sight, over the headland, in the record: set on
				// along its way, so it isn't seen again at the shore it stuck on (on land it comes back as handcarts)
				Vec3 here = lead.position();
				Vec3 flat = new Vec3(to.x - here.x, 0.0, to.z - here.z);
				double len = flat.length();
				Vec3 on = len > 1.0E-3 ? here.add(flat.scale(Math.min(SKIFF_SKIP, len) / len)) : here;
				put(server, c.at(BlockPos.containing(on.x, here.y, on.z)));
				vanish(level, c);
				return;
			}
			// (a river, a cliff, a wall: helped over it, the column with it)
			hop(level, carts, guards, to);
			live.improvedAt = now;
			live.best = Double.MAX_VALUE;
		}
		if (live.rig == TradeRoutes.BOAT) {
			// (0.7.490) skiffs keep to the water: each sails toward the one before it, the first toward the quay
			Entity before = null;
			for (Entity[] cart : carts) {
				if (cart[2] instanceof Wagon skiff) {
					skiff.sailTo(before == null ? to : before.position().add(Vec3.directionFromRotation(0.0F, before.getYRot()).scale(-3.5)));
				}
				before = cart[2];
			}
			return;
		}
		// the lead makes for a point ahead on the road; each cart behind follows the one before it
		Vec3 here = lead.position();
		Vec3 flat = new Vec3(to.x - here.x, 0.0, to.z - here.z);
		double len = flat.length();
		Vec3 road = Roads.ahead(level, here, goal, WAYPOINT);
		Vec3 ahead = road != null ? road : len > WAYPOINT ? here.add(flat.scale(WAYPOINT / len)) : to;
		if (road != null) {
			// (on the road: the guards flank the way it runs)
			flat = new Vec3(road.x - here.x, 0.0, road.z - here.z);
			len = flat.length();
		}
		BlockPos column = BlockPos.containing(ahead);
		if (level.isLoaded(column)) {
			column = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		}
		HEADING.put(carts.getFirst()[0].getUUID(), Vec3.atBottomCenterOf(column));
		for (int i = 1; i < carts.size(); i++) {
			Entity before = carts.get(i - 1)[2];
			Vec3 back = Vec3.directionFromRotation(0.0F, before.getYRot()).scale(-3.0);
			HEADING.put(carts.get(i)[0].getUUID(), before.position().add(back));
		}
		Vec3 facing = len > 1.0E-3 ? flat.scale(1.0 / len) : Vec3.ZERO;
		Vec3 right = new Vec3(-facing.z, 0.0, facing.x);
		for (int i = 0; i < guards.size(); i++) {
			PathfinderMob g = guards.get(i);
			Entity by = carts.get(Math.min(carts.size() - 1, i / 2))[1];
			HEADING.put(g.getUUID(), by.position().add(right.scale(i % 2 == 0 ? 3.0 : -3.0)).add(facing.scale(i == 0 ? 4.0 : 0.0)));
		}
		for (Entity[] cart : carts) {
			((PathfinderMob)cart[0]).setHomeTo(cart[0].blockPosition(), 64);
		}
		for (PathfinderMob g : guards) {
			g.setHomeTo(g.blockPosition(), 64);
		}
	}

	/**
	 * A rider out of the saddle (a horse dismounts its rider under water): back on once both are out of it; till then
	 * his horse wades after him.
	 */
	static void remount(Entity rider, Entity horse) {
		if (rider.getVehicle() == horse) {
			return;
		}
		if (rider.isInWater() || horse.isInWater()) {
			if (horse instanceof AbstractHorse h && rider.distanceTo(horse) > 3.0) {
				h.getNavigation().moveTo(rider, 1.0);
			}
			return;
		}
		if (rider.distanceTo(horse) > 3.0) {
			rider.teleportTo(horse.getX(), horse.getY(), horse.getZ());
		}
		rider.startRiding(horse);
	}

	/** The column set down further along its road. */
	private static void hop(ServerLevel level, List<Entity[]> carts, List<PathfinderMob> guards, Vec3 to) {
		for (Entity[] cart : carts) {
			hop(level, cart[0], cart[1], cart[2], to);
		}
		for (PathfinderMob g : guards) {
			Vec3 here = g.position();
			Vec3 flat = new Vec3(to.x - here.x, 0.0, to.z - here.z);
			double len = flat.length();
			if (len < 1.0E-3) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(here.add(flat.scale(Math.min(HOP, len) / len))));
			Cavalry.teleport(g, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		}
	}

	/** One cart (its carter, horse and wagon) helped over what stops it, toward {@code to}. */
	static void hop(ServerLevel level, Entity carter, Entity horse, Entity wagon, Vec3 to) {
		Vec3 here = horse.position();
		Vec3 flat = new Vec3(to.x - here.x, 0.0, to.z - here.z);
		double len = flat.length();
		if (len < 1.0E-3) {
			return;
		}
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(here.add(flat.scale(Math.min(HOP, len) / len))));
		poof(level, horse);
		Cavalry.teleport(carter, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		wagon.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, horse.getYRot(), 0.0F);
		poof(level, horse);
	}

	/** Comes into the world where it's got to: its carts in a column along its road, its guards beside them. */
	static void appear(ServerLevel level, Caravan c) {
		MinecraftServer server = level.getServer();
		Faction home = Factions.get(server, c.home());
		Faction lord = Factions.get(server, c.lord());
		if (home == null || lord == null) {
			return;
		}
		Live live = new Live(TAG_OF + c.id() + "." + Long.toHexString(level.getRandom().nextLong()));
		LIVE.put(c.id(), live);
		live.improvedAt = level.getGameTime();
		BlockPos goal = goal(server, c, home, lord);
		Vec3 at = Vec3.atBottomCenterOf(c.at());
		Vec3 flat = new Vec3(goal.getX() + 0.5 - at.x, 0.0, goal.getZ() + 0.5 - at.z);
		Vec3 facing = flat.length() < 1.0E-3 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		float yaw = (float)(Math.atan2(facing.z, facing.x) * (180.0 / Math.PI)) - 90.0F;
		// ((0.7.490) a trade's carts go by the rig it travels in: wagons, handcarts, or skiffs where there is water under it)
		int rig = TradeRoutes.rigNow(level, c);
		live.rig = rig;
		for (int i = 0; i < c.carts().size(); i++) {
			BlockPos spot = ground(level, at.subtract(facing.scale(i * SPACING)));
			if (rig == TradeRoutes.BOAT && i > 0 && !Wagon.water(level, spot.getX() + 0.5, spot.getZ() + 0.5)) {
				// ((0.7.490) no water at its place in the column: it sets off from the nearest water by it, else from the lead's)
				spot = water(level, spot, ground(level, at));
			}
			// ((0.7.294) with MCA, one of the fiefdom's people: Drivers; (0.7.490) an illager camp's, one of its pillagers; an ogre
			// hold's, one of its ogres)
			PathfinderMob carter;
			if (c.hold() >= 0) {
				carter = OgreHolds.hauler(level, spot, yaw);
			} else if (TradeRoutes.illagers(server, home)) {
				carter = TradeRoutes.illager(level, home, true, spot, yaw);
			} else {
				Worker hired = home.type() == FactionType.BARBARIAN ? carter(level, home, spot, yaw) : Drivers.hire(level, home, spot, yaw, TASK, 64, null);
				carter = hired == null ? null : hired.mob();
			}
			if (carter == null) {
				continue;
			}
			Worker w = carter instanceof Worker hired ? hired : null;
			carter.addTag(TAG);
			carter.addTag(live.stamp);
			Entity pull;
			Wagon wagon;
			if (c.hold() >= 0) {
				// ((0.7.490) an ogre between the shafts of a full cart, slow as a handcart: TradeRoutes.HAND_WALK)
				carter.addTag(TradeRoutes.PULL_TAG);
				wagon = Wagon.hitch(level, Wagon.CART, home, carter, c.id(), i);
				pull = carter;
			} else if (rig == TradeRoutes.HAND) {
				// (on foot between the handles, the cart behind him)
				carter.addTag(TradeRoutes.PULL_TAG);
				wagon = Wagon.hitch(level, Wagon.HANDCART, home, carter, c.id(), i);
				wagon.setTired(Math.min(1.0F, c.carts().get(i).values().stream().mapToInt(Integer::intValue).sum() / (float)TradeRoutes.HAND_LOAD));
				pull = carter;
			} else if (rig == TradeRoutes.BOAT) {
				// (a deckhand at the tiller of a skiff riding the water)
				wagon = Wagon.hitch(level, Wagon.SKIFF, home, carter, c.id(), i);
				wagon.snapTo(spot.getX() + 0.5, spot.getY() - 0.2, spot.getZ() + 0.5, yaw, 0.0F);
				carter.startRiding(wagon);
				pull = wagon;
			} else {
				// ((0.7.490) an illager carter on a draft horse of the camp's: TradeRoutes.horse)
				AbstractHorse horse = w != null ? Cavalry.mount(level, w, home, true) : TradeRoutes.horse(level, carter, home);
				if (horse == null) {
					McaCompat.vanish(carter);
					continue;
				}
				horse.addTag(TAG);
				horse.addTag(live.stamp);
				horse.setYRot(yaw);
				horse.setYBodyRot(yaw);
				wagon = Wagon.hitch(level, Wagon.CART, home, horse, c.id(), i);
				pull = horse;
			}
			wagon.addTag(TAG);
			wagon.addTag(live.stamp);
			wagon.setLoad(look(c.carts().get(i)));
			live.carts.add(new UUID[] {carter.getUUID(), pull.getUUID(), wagon.getUUID()});
		}
		for (int i = 0; i < c.guards() && rig != TradeRoutes.BOAT && c.hold() < 0; i++) {
			Vec3 right = new Vec3(-facing.z, 0.0, facing.x);
			BlockPos spot = ground(level, at.add(right.scale(i % 2 == 0 ? 3.0 : -3.0)).subtract(facing.scale(i / 2 * SPACING)));
			// ((0.7.490) a trade's guards may be an illager camp's own illagers: no Worker, a mob of the camp's)
			PathfinderMob guard;
			if (c.trade()) {
				guard = TradeRoutes.guard(level, home, spot, yaw);
			} else {
				Worker hired = guard(level, home, spot, yaw);
				guard = hired == null ? null : hired.mob();
			}
			if (guard == null) {
				continue;
			}
			guard.addTag(TAG);
			guard.addTag(live.stamp);
			if (c.mounted() && rig == TradeRoutes.CART && guard instanceof Worker w) {
				AbstractHorse horse = Cavalry.mount(level, w, home, true);
				if (horse != null) {
					horse.addTag(TAG);
					horse.addTag(live.stamp);
				}
			}
			live.guards.add(guard.getUUID());
		}
	}

	/**
	 * (0.7.490) A greenskin cart's carter for camp {@code f} at {@code at}: with MCA one of the camp's own goblins or orcs
	 * (raised as its grunt or goblin, then set to drive), else the folk carter {@link Drivers#hire} gives.
	 */
	static @Nullable Worker carter(ServerLevel level, Faction f, BlockPos at, float yaw) {
		if (McaCompat.LOADED && Hordes.greenskin(level.getServer(), f.id())) {
			Folk brute = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (brute != null) {
				brute.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
				brute.setup(Camps.orc(level.getServer(), f.id()) ? Orcs.GRUNT : Goblins.GOBLIN, f, at, 64);
				level.addFreshEntity(brute);
				if (McaCompat.raider(level, brute) instanceof Worker v && v.mob() instanceof Enlisted e) {
					// (his body the camp's, his job the cart's: Drivers.of knows him by the carter's role)
					e.enlist(CARTER, f.id());
					v.setTask(TASK);
					return v;
				}
				if (brute.isAlive()) {
					brute.discard();
				}
			}
		}
		return Drivers.hire(level, f, at, yaw, TASK, 64, null);
	}

	/** (0.7.294) One of a caravan's guards for {@code f} at {@code at}: with MCA an MCA guard of its people, else a folk. */
	static @Nullable Worker guard(ServerLevel level, Faction f, BlockPos at, float yaw) {
		if (f.type() == FactionType.BARBARIAN) {
			// ((0.7.490) a greenskin cart's guards are the camp's own goblins or orcs, MCA villagers with MCA)
			Folk brute = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
			if (brute == null) {
				return null;
			}
			brute.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
			brute.setup(Camps.orc(level.getServer(), f.id()) ? Orcs.GRUNT : Goblins.GOBLIN, f, at, 64);
			brute.setTask(TASK);
			level.addFreshEntity(brute);
			Worker man = brute;
			if (McaCompat.raider(level, brute) instanceof Worker v) {
				v.setTask(TASK);
				man = v;
			}
			return man;
		}
		if (f.type().isRealm()) {
			Worker w = McaCompat.escort(level, f, at, yaw, TASK, 64);
			if (w != null) {
				return w;
			}
		}
		Folk guard = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
		if (guard == null) {
			return null;
		}
		guard.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
		guard.setup(Folk.GUARD, f, at, 64);
		guard.setTask(TASK);
		level.addFreshEntity(guard);
		return guard;
	}

	/** (0.7.490) The nearest open water to {@code from} within {@link #SKIFF_SEARCH} blocks, at the surface; else {@code otherwise}. */
	static BlockPos water(ServerLevel level, BlockPos from, BlockPos otherwise) {
		for (int r = 1; r <= SKIFF_SEARCH; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					if (Wagon.water(level, from.getX() + dx + 0.5, from.getZ() + dz + 0.5)) {
						return ground(level, Vec3.atBottomCenterOf(from.offset(dx, 0, dz)));
					}
				}
			}
		}
		return otherwise;
	}

	static BlockPos ground(ServerLevel level, Vec3 at) {
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(at));
	}

	/** Its carts' looks brought up to date (loaded again for home, or empty). */
	private static void restyle(ServerLevel level, Caravan c, Live live) {
		for (int i = 0; i < live.carts.size() && i < c.carts().size(); i++) {
			if (level.getEntity(live.carts.get(i)[2]) instanceof Wagon w) {
				w.setLoad(look(c.carts().get(i)));
			}
		}
	}

	/** What a load looks like on its cart. */
	static int look(Map<String, Integer> cart) {
		int food = 0, stone = 0, all = 0;
		for (Map.Entry<String, Integer> e : cart.entrySet()) {
			Item item = item(e.getKey());
			if (item == null) {
				continue;
			}
			all += e.getValue();
			ItemStack sample = new ItemStack(item);
			if (Storage.isFood(sample) || sample.is(Items.WHEAT)) {
				food += e.getValue();
			} else if (Focus.MINING_GOODS.contains(item)) {
				stone += e.getValue();
			}
		}
		if (all == 0) {
			return Wagon.EMPTY;
		}
		return food * 3 >= all * 2 ? Wagon.FOOD : stone * 3 >= all * 2 ? Wagon.STONE : Wagon.GOODS;
	}

	/** Out of the world (it goes on in the record, or it's done): its folk, horses and carts gone. */
	static void vanish(ServerLevel level, Caravan c) {
		Live live = LIVE.remove(c.id());
		if (live == null) {
			return;
		}
		for (UUID[] ids : live.carts) {
			for (UUID id : new java.util.LinkedHashSet<>(List.of(ids))) {
				Entity e = level.getEntity(id);
				if (e != null) {
					HEADING.remove(id);
					McaCompat.vanish(e);
				}
			}
		}
		for (UUID id : live.guards) {
			Entity e = level.getEntity(id);
			HEADING.remove(id);
			if (e != null) {
				Cavalry.dismiss(e);
				McaCompat.vanish(e);
			}
		}
	}

	/** Every cart lost on the road: the caravan is over. */
	private static void lost(ServerLevel level, Caravan c) {
		MinecraftServer server = level.getServer();
		vanish(level, c);
		remove(server, c, level.getGameTime() + gap(server, c));
		JaymosKingdoms.LOGGER.info("The caravan from {} was lost on the road", nameOf(server, c.home()));
	}

	// ---- carts in trouble -------------------------------------------------------------------------------------------

	/** A cart left without its horse or carter: it breaks open where it stands, its load spilled, and it's off the caravan. */
	static void cartLost(ServerLevel level, Wagon wagon) {
		spill(level, wagon);
	}

	/** A player strikes a cart: the caravan's guards turn on him. */
	static void struck(ServerLevel level, Wagon wagon, ServerPlayer player) {
		Faction f = Factions.get(level.getServer(), wagon.faction());
		if (f == null) {
			return;
		}
		Economy.noteAttack(level.getServer(), f, player);
		// ((0.7.294) folk or MCA guards)
		for (Worker g : Workers.near(level, wagon.getBoundingBox().inflate(24.0), x -> x.factionId() == f.id()
			&& (x instanceof Folk folk ? folk.combatant() : x.role() == Folk.GUARD || x.role() == Folk.ARCHER || Greenskins.isRole(x.role()) || Barbarians.villagerRole(x.role())))) {
			if (Rulers.target(g.mob()) == null) {
				Rulers.charge(g.mob(), player);
			}
		}
	}

	/** A player broke a cart open: its load spilled for the taking, and the fiefdom and its overlord think the worse of him. */
	static void robbed(ServerLevel level, Wagon wagon, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, wagon.faction());
		String tribe = f != null && f.type() == FactionType.BARBARIAN ? Tribes.of(server, f.id()) : null;
		if (tribe != null) {
			// ((0.7.490) a greenskin cart: the tribe's grudge against him rises)
			Tribes.add(server, player.getUUID(), tribe, GREEN_ROB_GRUDGE);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.caravan.robbed.green", Tribes.name(tribe)).withStyle(ChatFormatting.RED));
		} else if (f != null && get(server, wagon.caravan()) instanceof Caravan trade && trade.trade()) {
			// ((0.7.490) a settlement's trade: the cart's rig and its owner's kind say what it costs him)
			TradeRoutes.robbed(level, wagon, player, f, trade);
		} else if (f != null) {
			Reputation.change(player, f, REP_ROB);
			Faction lord = Relations.overlord(server, f);
			if (lord != null) {
				Reputation.change(player, lord, REP_ROB);
			}
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.caravan.robbed", f.name()).withStyle(ChatFormatting.RED));
		}
		spill(level, wagon);
	}

	/** {@code wagon} breaks: its load on the ground, its horse and carter gone home, its place in the caravan emptied. */
	private static void spill(ServerLevel level, Wagon wagon) {
		MinecraftServer server = level.getServer();
		Caravan c = get(server, wagon.caravan());
		level.playSound(null, wagon.blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.NEUTRAL, 1.0F, 1.0F);
		level.levelEvent(2001, wagon.blockPosition(), Block.getId(net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState()));
		if (wagon.caravan() == Wagon.WAR_CART) {
			WarParties.cartBroken(level, wagon);
		}
		// ((0.7.490) a greenskin cart may have a war journal among its goods)
		Faction owner = Factions.get(server, wagon.faction());
		if (owner != null) {
			LoreBooks.cart(level, owner, wagon.blockPosition());
		}
		if (c != null && wagon.slot() < c.carts().size()) {
			for (Map.Entry<String, Integer> e : c.carts().get(wagon.slot()).entrySet()) {
				Item item = item(e.getKey());
				int left = e.getValue();
				while (item != null && left > 0) {
					int n = Math.min(left, item.getDefaultMaxStackSize());
					Block.popResource(level, wagon.blockPosition(), new ItemStack(item, n));
					left -= n;
				}
			}
			List<Map<String, Integer>> carts = new ArrayList<>(c.carts());
			carts.set(wagon.slot(), Map.of());
			put(server, c.carts(carts));
		}
		Entity horse = wagon.horse();
		if (horse instanceof Worker || horse != null && (wagon.kind() == Wagon.HANDCART || wagon.kind() == Wagon.SKIFF)) {
			// ((0.7.490) a handcart's puller, a skiff's deckhand: the carter himself is what it's hitched to)
			HEADING.remove(horse.getUUID());
			poof(level, horse);
			McaCompat.vanish(horse);
		} else if (horse != null) {
			for (Entity rider : horse.getPassengers()) {
				HEADING.remove(rider.getUUID());
				poof(level, rider);
				McaCompat.vanish(rider);
			}
			horse.discard();
		}
		wagon.discard();
	}

	/** What a cart carries, for a player who looks. */
	static Component describe(ServerLevel level, Wagon wagon) {
		MinecraftServer server = level.getServer();
		if (wagon.caravan() == Wagon.WAR_CART) {
			return WarParties.describe(level, wagon);
		}
		Caravan c = get(server, wagon.caravan());
		if (c == null || wagon.slot() >= c.carts().size() || c.carts().get(wagon.slot()).isEmpty()) {
			return Component.translatable("message.jaymoskingdoms.caravan.empty_cart", nameOf(server, wagon.faction()));
		}
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, Integer> e : c.carts().get(wagon.slot()).entrySet()) {
			Item item = item(e.getKey());
			if (item != null) {
				parts.add(e.getValue() + " " + new ItemStack(item).getHoverName().getString());
			}
		}
		String where = nameOf(server, c.back() ? c.home() : c.lord());
		// ((0.7.490) a handcart, a skiff: said as what it is)
		String what = wagon.kind() == Wagon.HANDCART ? "message.jaymoskingdoms.caravan.handcart" : wagon.kind() == Wagon.SKIFF ? "message.jaymoskingdoms.caravan.skiff"
			: "message.jaymoskingdoms.caravan.cart";
		return Component.translatable(what, nameOf(server, wagon.faction()), where, String.join(", ", parts));
	}

	// ---- its folk ---------------------------------------------------------------------------------------------------

	/** One of a caravan's carters or guards (no one on its fiefdom's roster): (0.7.294) a folk or a villager. */
	static boolean isCaravanFolk(Worker folk) {
		return TASK.equals(folk.task());
	}

	/** A caravan folk, horse or cart loaded from disk that isn't its caravan's now (left in a chunk that unloaded): gone. */
	private static void loaded(Entity entity) {
		if (!entity.entityTags().contains(TAG)) {
			return;
		}
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(TAG_OF)) {
				String rest = tag.substring(TAG_OF.length());
				int dot = rest.indexOf('.');
				int id;
				try {
					id = Integer.parseInt(dot < 0 ? rest : rest.substring(0, dot));
				} catch (NumberFormatException e) {
					id = -1;
				}
				Live live = LIVE.get(id);
				if (live != null && live.stamp.equals(tag)) {
					return;
				}
			}
		}
		McaCompat.vanish(entity);
	}

	/**
	 * (0.7.294) A caravan's villager ({@code Workers.step}, before its brain): steered where the caravan sends it, true
	 * (in place of its brain); false to let its brain have it (a guard's fight, nothing to steer for).
	 */
	static boolean villagerStep(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		Vec3 to = HEADING.get(mob.getUUID());
		// (a guard's brain has him for a fight: a foe on him, or near, which only his brain's senses would find)
		if (mob instanceof Folk || to == null || w.role() != CARTER && (Rulers.target(mob) != null || Rulers.foeNear(level, mob))
			|| McaCompat.busy(mob)) {
			return w.role() == CARTER && (mob.isPassenger() || mob.entityTags().contains(TradeRoutes.PULL_TAG));
		}
		double d = mob.position().distanceTo(to);
		if (d < 2.0) {
			mob.getNavigation().stop();
		} else if (mob.getNavigation().isDone() || mob.tickCount % 10 == 0) {
			mob.getNavigation().moveTo(to.x, to.y, to.z, pace(mob, w.role() == CARTER, d));
		}
		return true;
	}

	/** (0.5.28) Steers a caravan folk (a war party's carter too) toward {@code to}, or (null) lets it be. */
	static void steer(Entity folk, @Nullable Vec3 to) {
		if (to == null) {
			HEADING.remove(folk.getUUID());
		} else {
			HEADING.put(folk.getUUID(), to);
		}
	}

	/** Steers a caravan's folk where their caravan sends them. */
	static final class Drive extends Goal {
		private final Folk folk;

		Drive(Folk folk) {
			this.folk = folk;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return isCaravanFolk(this.folk) && this.folk.getTarget() == null && HEADING.containsKey(this.folk.getUUID());
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			this.folk.getNavigation().stop();
		}

		@Override
		public void tick() {
			Vec3 to = HEADING.get(this.folk.getUUID());
			if (to == null) {
				return;
			}
			double d = this.folk.position().distanceTo(to);
			if (d < 2.0) {
				this.folk.getNavigation().stop();
			} else if (this.folk.getNavigation().isDone() || this.folk.tickCount % 10 == 0) {
				this.folk.getNavigation().moveTo(to.x, to.y, to.z, pace(this.folk, this.folk.role() == CARTER, d));
			}
		}
	}


	/**
	 * (0.7.490) The pace a caravan's man walks at, {@code d} from where he's sent: a carter (in the saddle or at the
	 * tiller) at {@link #DRIVE_SPEED}, a handcart's puller slower ({@link TradeRoutes#HAND_WALK}, the cart's weight in
	 * his hands), a guard at his own pace till he's near.
	 */
	static double pace(PathfinderMob mob, boolean carter, double d) {
		if (mob.entityTags().contains(TradeRoutes.PULL_TAG)) {
			return TradeRoutes.HAND_WALK;
		}
		return carter || d < 6.0 ? DRIVE_SPEED : 1.0;
	}

	/**
	 * (0.7.490) Steers a caravan's man that is neither folk nor villager (an illager camp's pillager or vindicator, an
	 * ogre hold's ogre) where its caravan sends it, as {@link Drive} does a folk; it fights first when it has a foe.
	 */
	static final class Haul extends Goal {
		private final PathfinderMob mob;
		private final boolean carter;

		Haul(PathfinderMob mob, boolean carter) {
			this.mob = mob;
			this.carter = carter;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return this.mob.getTarget() == null && HEADING.containsKey(this.mob.getUUID());
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			this.mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			Vec3 to = HEADING.get(this.mob.getUUID());
			if (to == null) {
				return;
			}
			double d = this.mob.position().distanceTo(to);
			if (d < 2.0) {
				this.mob.getNavigation().stop();
			} else if (this.mob.getNavigation().isDone() || this.mob.tickCount % 10 == 0) {
				this.mob.getNavigation().moveTo(to.x, to.y, to.z, pace(this.mob, this.carter, d));
			}
		}
	}

	private static void poof(ServerLevel level, Entity e) {
		level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1.0, e.getZ(), 10, 0.4, 0.5, 0.4, 0.02);
	}
}
