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
 * (0.5.19) Caravans: every fiefdom sends its surplus to the kingdom it serves in horse-drawn storage carts, a few times
 * a day, and they come back with what it lacks. A mining fiefdom sends stone and ore and gets food back; a farming one
 * sends food and gets timber, coal and iron ({@link Focus}).
 *
 * <p><b>On the road.</b> A caravan is a record (saved on the Overworld) with its carts' loads and where it is. Where a
 * player is about, it's there to see: each cart a {@link Wagon} hitched to a horse with its carter (a {@link #CARTER})
 * in the saddle, the carts in a column on the straight road between the two seats, its guards (the fiefdom's, mounted
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
	/** A caravan on the road longer than this (ticks) is given up (lost to something the world did). */
	static final long LONGEST = 24000L * 2;
	/** Reputation: a cart broken open by a player (with the fiefdom, and its overlord). */
	static final int REP_ROB = -60;
	/** Players this close to either seat hear of it. */
	static final double TELL_RANGE = 96.0;

	static final String TAG = "jaymoskingdoms.caravan", TAG_OF = "jaymoskingdoms.caravan_of.";
	/** A caravan folk's task. */
	static final String TASK = "caravan";

	/** One caravan on the road: whose, to whom, which way ({@code back}), where it is, its carts' loads, its guards. */
	record Caravan(int id, int home, int lord, boolean back, BlockPos at, List<Map<String, Integer>> carts, int guards, boolean mounted,
		long started, long stopUntil) {
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
				Codec.LONG.optionalFieldOf("stop_until", 0L).forGetter(Caravan::stopUntil))
			.apply(i, Caravan::new));

		Caravan at(BlockPos pos) {
			return new Caravan(this.id, this.home, this.lord, this.back, pos.immutable(), this.carts, this.guards, this.mounted, this.started, this.stopUntil);
		}

		Caravan carts(List<Map<String, Integer>> carts) {
			return new Caravan(this.id, this.home, this.lord, this.back, this.at, List.copyOf(carts), this.guards, this.mounted, this.started, this.stopUntil);
		}

		Caravan turned(List<Map<String, Integer>> carts, long stopUntil) {
			return new Caravan(this.id, this.home, this.lord, !this.back, this.at, List.copyOf(carts), this.guards, this.mounted, this.started, stopUntil);
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
		});
	}

	// ---- the ledger --------------------------------------------------------------------------------------------------

	static Ledger ledger(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(CARAVANS, Ledger.EMPTY);
	}

	private static void save(MinecraftServer server, Ledger ledger) {
		Factions.level(server).setAttached(CARAVANS, ledger);
	}

	private static void put(MinecraftServer server, Caravan c) {
		Ledger l = ledger(server);
		Map<String, Caravan> all = new HashMap<>(l.caravans());
		all.put(Integer.toString(c.id()), c);
		save(server, new Ledger(all, l.next(), l.pending(), l.nextId()));
	}

	private static void remove(MinecraftServer server, Caravan c, long nextSend) {
		Ledger l = ledger(server);
		Map<String, Caravan> all = new HashMap<>(l.caravans());
		all.remove(Integer.toString(c.id()));
		Map<String, Long> next = new HashMap<>(l.next());
		next.put(Integer.toString(c.home()), nextSend);
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
		Caravan c = new Caravan(id, f.id(), lord.id(), false, start(f), carts, guards, mounted, now, now + STOP_TICKS / 2);
		Map<String, Caravan> all = new HashMap<>(l.caravans());
		all.put(Integer.toString(id), c);
		Map<String, Long> next = new HashMap<>(l.next());
		next.put(Integer.toString(f.id()), now + CARAVAN_TICKS);
		save(server, new Ledger(all, next, l.pending(), id + 1));
		tell(level, f.seat(), Component.translatable("message.jaymoskingdoms.caravan.sets_out", f.name(), carts.size(), lord.name(), load)
			.withStyle(ChatFormatting.GOLD));
		JaymosKingdoms.LOGGER.info("{} sends a caravan of {} carts ({} goods) to {}", f.name(), carts.size(), load, lord.name());
		if (level.isPositionEntityTicking(c.at())) {
			appear(level, c);
		}
		return c;
	}

	private static void later(MinecraftServer server, Faction f, long when) {
		Ledger l = ledger(server);
		Map<String, Long> next = new HashMap<>(l.next());
		next.put(Integer.toString(f.id()), when);
		save(server, new Ledger(l.caravans(), next, l.pending(), l.nextId()));
	}

	/** Where a faction's caravans load and unload: its storehouse door, else its seat. */
	static BlockPos start(Faction f) {
		return f.holdings().depot().orElse(f.seat());
	}

	/** Takes from {@code f}'s storage what it has of {@code what} beyond {@code keep} of each, at most {@code most}, a cart's load at a time. */
	private static List<Map<String, Integer>> loadUp(ServerLevel level, Faction f, Predicate<ItemStack> what, int keep, int most) {
		List<Map<String, Integer>> carts = new ArrayList<>();
		Map<String, Integer> cart = new LinkedHashMap<>();
		int inCart = 0, total = 0;
		for (Map.Entry<Item, Integer> e : Storage.totals(level, f).entrySet()) {
			ItemStack sample = new ItemStack(e.getKey());
			int spare = e.getValue() - keep;
			if (spare <= 0 || !what.test(sample)) {
				continue;
			}
			while (spare > 0 && total < most) {
				int take = Math.min(64, Math.min(spare, Math.min(CART_LOAD - inCart, most - total)));
				ItemStack taken = Storage.take(level, f, e.getKey(), take);
				if (taken.isEmpty()) {
					break;
				}
				cart.merge(key(e.getKey()), taken.getCount(), Integer::sum);
				inCart += taken.getCount();
				total += taken.getCount();
				spare -= taken.getCount();
				if (inCart >= CART_LOAD) {
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
	private static void deliver(ServerLevel level, Faction f, Map<String, Integer> goods) {
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
		if (!c.back()) {
			int load = c.load();
			if (lord != null) {
				for (Map<String, Integer> cart : c.carts()) {
					deliver(level, lord, cart);
				}
				tell(level, lord.seat(), Component.translatable("message.jaymoskingdoms.caravan.arrives", nameOf(server, c.home()), lord.name(), load)
					.withStyle(ChatFormatting.GOLD));
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
				remove(server, c, now + CARAVAN_TICKS);
				continue;
			}
			BlockPos goal = c.back() ? start(home) : start(lord);
			Live live = LIVE.get(c.id());
			if (live != null) {
				drive(level, c, live, goal, now);
				continue;
			}
			// unseen: on along the road, or round at its end
			if (level.isPositionEntityTicking(c.at())) {
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
			Vec3 next = at.add(to.subtract(at).scale(Math.min(1.0, ROAD_SPEED * TICK / d)));
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
			remove(server, c, now + CARAVAN_TICKS);
			Faction home = Factions.get(server, c.home());
			if (home != null) {
				tell(level, home.seat(), Component.translatable("message.jaymoskingdoms.caravan.home", home.name()).withStyle(ChatFormatting.GOLD));
			}
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
				remount(carter, horse);
				if (!level.isPositionEntityTicking(wagon.blockPosition()) && level.isPositionEntityTicking(horse.blockPosition())) {
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
		List<Folk> guards = new ArrayList<>();
		for (UUID id : live.guards) {
			if (level.getEntity(id) instanceof Folk g && g.isAlive()) {
				guards.add(g);
			}
		}
		for (Folk g : guards) {
			if (c.mounted() && !g.isPassenger()) {
				AbstractHorse spare = level.getEntitiesOfClass(AbstractHorse.class, g.getBoundingBox().inflate(12.0),
					h -> h.isAlive() && !h.isVehicle() && h.entityTags().contains(live.stamp)).stream().findFirst().orElse(null);
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
				HEADING.remove(cart[0].getUUID());
			}
			for (Folk g : guards) {
				HEADING.remove(g.getUUID());
			}
			if (now % 40 == 0) {
				Entity w = carts.get(level.getRandom().nextInt(carts.size()))[2];
				level.playSound(null, w.blockPosition(), SoundEvents.BARREL_CLOSE, SoundSource.NEUTRAL, 0.8F, 0.9F);
			}
			return;
		}
		if (d <= ARRIVE) {
			reached(level, c, now);
			return;
		}
		if (d < live.best - 0.5) {
			live.best = d;
			live.improvedAt = now;
		} else if (now - live.improvedAt > STUCK_TICKS) {
			// (a river, a cliff, a wall: helped over it, the column with it)
			hop(level, carts, guards, to);
			live.improvedAt = now;
			live.best = Double.MAX_VALUE;
		}
		// the lead makes for a point ahead on the road; each cart behind follows the one before it
		Vec3 here = lead.position();
		Vec3 flat = new Vec3(to.x - here.x, 0.0, to.z - here.z);
		double len = flat.length();
		Vec3 ahead = len > WAYPOINT ? here.add(flat.scale(WAYPOINT / len)) : to;
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
			Folk g = guards.get(i);
			Entity by = carts.get(Math.min(carts.size() - 1, i / 2))[1];
			HEADING.put(g.getUUID(), by.position().add(right.scale(i % 2 == 0 ? 3.0 : -3.0)).add(facing.scale(i == 0 ? 4.0 : 0.0)));
		}
		for (Entity[] cart : carts) {
			((Folk)cart[0]).setHomeTo(cart[0].blockPosition(), 64);
		}
		for (Folk g : guards) {
			g.setHomeTo(g.blockPosition(), 64);
		}
	}

	/**
	 * A rider out of the saddle (a horse dismounts its rider under water): back on once both are out of it; till then
	 * his horse wades after him.
	 */
	private static void remount(Entity rider, Entity horse) {
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
	private static void hop(ServerLevel level, List<Entity[]> carts, List<Folk> guards, Vec3 to) {
		for (Entity[] cart : carts) {
			Vec3 here = cart[1].position();
			Vec3 flat = new Vec3(to.x - here.x, 0.0, to.z - here.z);
			double len = flat.length();
			if (len < 1.0E-3) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(here.add(flat.scale(Math.min(HOP, len) / len))));
			poof(level, cart[1]);
			Cavalry.teleport(cart[0], at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			cart[2].snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, cart[1].getYRot(), 0.0F);
			poof(level, cart[1]);
		}
		for (Folk g : guards) {
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

	/** Comes into the world where it's got to: its carts in a column along its road, its guards beside them. */
	private static void appear(ServerLevel level, Caravan c) {
		MinecraftServer server = level.getServer();
		Faction home = Factions.get(server, c.home());
		Faction lord = Factions.get(server, c.lord());
		if (home == null || lord == null) {
			return;
		}
		Live live = new Live(TAG_OF + c.id() + "." + Long.toHexString(level.getRandom().nextLong()));
		LIVE.put(c.id(), live);
		live.improvedAt = level.getGameTime();
		BlockPos goal = c.back() ? start(home) : start(lord);
		Vec3 at = Vec3.atBottomCenterOf(c.at());
		Vec3 flat = new Vec3(goal.getX() + 0.5 - at.x, 0.0, goal.getZ() + 0.5 - at.z);
		Vec3 facing = flat.length() < 1.0E-3 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		float yaw = (float)(Math.atan2(facing.z, facing.x) * (180.0 / Math.PI)) - 90.0F;
		for (int i = 0; i < c.carts().size(); i++) {
			BlockPos spot = ground(level, at.subtract(facing.scale(i * SPACING)));
			Folk carter = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
			if (carter == null) {
				continue;
			}
			carter.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0F);
			carter.setup(CARTER, home, spot, 64);
			carter.setTask(TASK);
			carter.addTag(TAG);
			carter.addTag(live.stamp);
			level.addFreshEntity(carter);
			AbstractHorse horse = Cavalry.mount(level, carter, home, true);
			if (horse == null) {
				carter.discard();
				continue;
			}
			horse.addTag(TAG);
			horse.addTag(live.stamp);
			horse.setYRot(yaw);
			horse.setYBodyRot(yaw);
			Wagon wagon = Wagon.hitch(level, Wagon.CART, home, horse, c.id(), i);
			wagon.addTag(TAG);
			wagon.addTag(live.stamp);
			wagon.setLoad(look(c.carts().get(i)));
			live.carts.add(new UUID[] {carter.getUUID(), horse.getUUID(), wagon.getUUID()});
		}
		for (int i = 0; i < c.guards(); i++) {
			Vec3 right = new Vec3(-facing.z, 0.0, facing.x);
			BlockPos spot = ground(level, at.add(right.scale(i % 2 == 0 ? 3.0 : -3.0)).subtract(facing.scale(i / 2 * SPACING)));
			Folk guard = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
			if (guard == null) {
				continue;
			}
			guard.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0F);
			guard.setup(Folk.GUARD, home, spot, 64);
			guard.setTask(TASK);
			guard.addTag(TAG);
			guard.addTag(live.stamp);
			level.addFreshEntity(guard);
			if (c.mounted()) {
				AbstractHorse horse = Cavalry.mount(level, guard, home, true);
				if (horse != null) {
					horse.addTag(TAG);
					horse.addTag(live.stamp);
				}
			}
			live.guards.add(guard.getUUID());
		}
	}

	private static BlockPos ground(ServerLevel level, Vec3 at) {
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
	private static void vanish(ServerLevel level, Caravan c) {
		Live live = LIVE.remove(c.id());
		if (live == null) {
			return;
		}
		for (UUID[] ids : live.carts) {
			for (UUID id : ids) {
				Entity e = level.getEntity(id);
				if (e != null) {
					HEADING.remove(id);
					e.discard();
				}
			}
		}
		for (UUID id : live.guards) {
			Entity e = level.getEntity(id);
			HEADING.remove(id);
			if (e instanceof Folk g) {
				Cavalry.dismiss(g);
			}
			if (e != null) {
				e.discard();
			}
		}
	}

	/** Every cart lost on the road: the caravan is over. */
	private static void lost(ServerLevel level, Caravan c) {
		MinecraftServer server = level.getServer();
		vanish(level, c);
		remove(server, c, level.getGameTime() + CARAVAN_TICKS);
		Faction home = Factions.get(server, c.home());
		if (home != null) {
			tell(level, home.seat(), Component.translatable("message.jaymoskingdoms.caravan.lost", home.name()).withStyle(ChatFormatting.RED));
		}
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
		for (Folk g : level.getEntitiesOfClass(Folk.class, wagon.getBoundingBox().inflate(24.0), x -> x.factionId() == f.id() && x.combatant())) {
			if (g.getTarget() == null) {
				g.setTarget(player);
			}
		}
	}

	/** A player broke a cart open: its load spilled for the taking, and the fiefdom and its overlord think the worse of him. */
	static void robbed(ServerLevel level, Wagon wagon, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, wagon.faction());
		if (f != null) {
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
		if (horse != null) {
			for (Entity rider : horse.getPassengers()) {
				HEADING.remove(rider.getUUID());
				poof(level, rider);
				rider.discard();
			}
			horse.discard();
		}
		wagon.discard();
	}

	/** What a cart carries, for a player who looks. */
	static Component describe(ServerLevel level, Wagon wagon) {
		MinecraftServer server = level.getServer();
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
		return Component.translatable("message.jaymoskingdoms.caravan.cart", nameOf(server, wagon.faction()), where, String.join(", ", parts));
	}

	// ---- its folk ---------------------------------------------------------------------------------------------------

	/** One of a caravan's carters or guards (no one on its fiefdom's roster). */
	static boolean isCaravanFolk(Folk folk) {
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
		entity.discard();
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
				this.folk.getNavigation().moveTo(to.x, to.y, to.z, this.folk.role() == CARTER || d < 6.0 ? DRIVE_SPEED : 1.0);
			}
		}
	}

	// ---- telling ----------------------------------------------------------------------------------------------------

	private static void tell(ServerLevel level, BlockPos near, Component message) {
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(near, TELL_RANGE)) {
				p.sendSystemMessage(message);
			}
		}
	}

	private static void poof(ServerLevel level, Entity e) {
		level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1.0, e.getZ(), 10, 0.4, 0.5, 0.4, 0.02);
	}
}
