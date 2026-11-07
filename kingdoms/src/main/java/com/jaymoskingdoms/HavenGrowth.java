package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Havens expand (Andy, 2026-11-03: "they only trade, but expand massively to maximize output. They have fierce
 * fighters who are sworn to defend."). A haven has no tiers of the camp kind and takes no tribute beyond what it had; it
 * grows by what it makes and sells:
 * <ul>
 * <li><b>Works.</b> It keeps raising production buildings by its blood ({@link HavenBuildings}): fields for every blood,
 * workshops for every blood, a mine for the Stoneborn and Warrior-born, a dock for the Tideborn, a mill for the Sylvan (a
 * mixed haven, all of them). How many it raises rises with its size ({@link #max}); each kind of settlement sets its own
 * most ({@link #cap}). They go up wherever its land and stores allow ({@link Planner#eligible} asks {@link #allows}).</li>
 * <li><b>Output.</b> Each work built makes its goods each day into the haven's storage ({@link #produce}, from
 * {@link Production#produce}): food from the fields, ore and cut gems from the mine, catch and pearls from the dock, timber
 * and herbs from the mill, ingots, hides, arrows or string from the workshop, and a little trade income besides. A trader's
 * wares last longer for it ({@link #stock}, {@link HavenWares}), and the surplus goes out by caravan ({@link Caravans}) to a
 * settlement that will buy it.</li>
 * <li><b>Land.</b> As it grows it claims more land, ring by ring, where no one holds it already ({@link #grow}), paid from
 * its treasury (a realm) or its food (a village).</li>
 * <li><b>The sworn order.</b> A haven keeps a sworn order of fighters by blood ({@link #order}): Tideborn harpooners,
 * Stoneborn shield-wardens, Sylvan wardens (archers), Warrior-born sworn blades. They grow in number with the haven
 * ({@link #target}), fight harder than other guards ({@link Oath}) and never run; when anyone of the haven is struck by
 * an outsider they rally and fall on him together ({@link #rally}).</li>
 * </ul>
 */
public final class HavenGrowth {
	// ---- its works ---------------------------------------------------------------------------------------------------

	/** The catalogue's own most for a work (the real most is {@link #max}); the size each is first built from. */
	static final int MOST_BUILT = 12, FIELDS_FROM = 14, WORKSHOP_FROM = 18, MINE_FROM = 22, DOCK_FROM = 20, MILL_FROM = 18;
	/** Another of each work for every this many in its size past the first. */
	static final int SIZE_PER_WORK = 8;
	/** Most of each work by the kind of settlement (fields, workshop, mine, dock, mill): a village, a kingdom, a fiefdom. */
	private static final int[] VILLAGE_MOST = {6, 3, 2, 3, 3}, KINGDOM_MOST = {10, 5, 4, 4, 4}, FIEFDOM_MOST = {8, 4, 3, 3, 3};
	private static final List<String> WORKS = List.of(HavenBuildings.FIELDS, HavenBuildings.WORKSHOP, HavenBuildings.MINE, HavenBuildings.DOCK,
		HavenBuildings.MILL);

	// ---- its output (per work, per day) ------------------------------------------------------------------------------

	/** Food from a field, ore and rock from a mine, catch from a dock, timber from a mill, goods from a workshop. */
	static final int FIELD_FOOD = 10, MINE_ROCK = 20, MINE_ORES = 4, DOCK_CATCH = 10, MILL_LOGS = 16, WORKSHOP_GOODS = 6;
	/** A mine's cut gems, a dock's deep fish, a mill's herbs, a workshop's own ware, a day. */
	static final int MINE_GEMS = 1, DOCK_FISH = 2, MILL_HERBS = 2, WORKSHOP_WARES = 1;
	/** One day in this many a dock brings up a pearl. */
	static final int PEARL_ONE_IN = 3;
	/** Emeralds a work earns a day in trade (a realm's treasury; a village's storage). */
	static final int TRADE_INCOME = 1;
	/** A trader's wares last this many more uses for each work built, at most this many more. */
	static final int STOCK_PER_WORK = 2, STOCK_MOST = 16;

	// ---- its land ----------------------------------------------------------------------------------------------------

	/**
	 * How often it thinks of claiming more land (ticks); the size it claims its first ring past its founding land from, and
	 * another ring for every this many in its size past that. (0.7.490) Rings count out from the land it was founded with
	 * (see {@link #claimRadius}), not from a fixed start inside it.
	 */
	static final long CLAIM_TICKS = 1200L;
	static final int CLAIM_FROM = 14, SIZE_PER_RING = 10;
	/** The furthest out it claims (chunks) by kind: a village, a kingdom, a fiefdom (each above the land it is founded with). */
	static final int VILLAGE_CLAIM = 5, KINGDOM_CLAIM = 10, FIEFDOM_CLAIM = 8;
	/** A realm pays this many emeralds for each chunk; a village needs this much food in store to claim at all. */
	static final int CLAIM_COST = 4, CLAIM_FOOD = 32;
	/**
	 * (0.7.490) Once it holds all it may claim, it looks again only when it grows, loses land, or this long has passed
	 * (ticks); when it can't pay for land that is free, it waits this long before it looks again.
	 */
	static final long CLAIM_RECHECK = 24000L, CLAIM_RETRY = 6000L;

	// ---- its sworn order ---------------------------------------------------------------------------------------------

	/** How often the order is looked to (ticks), and how long before another is sworn in (ticks). */
	static final long ORDER_TICKS = 200L, SWEAR_GAP = 1200L;
	/** The sworn it keeps: this many, plus one for every this many in its size (not counting the sworn), at most this many by kind. */
	static final int ORDER_BASE = 2, FOLK_PER_SWORN = 4, VILLAGE_ORDER = 16, KINGDOM_ORDER = 28, FIEFDOM_ORDER = 10;
	/** Swearing one in takes this much food, and the haven must have at least this much in store (none, if it has no storehouse at all). */
	static final int OATH_FOOD = 4, OATH_FOOD_LEAST = 16;
	/** The rally: how far it reaches (blocks), how long the sworn fight the harder (ticks), how often it can be called (ticks). */
	static final double RALLY_RANGE = 40.0;
	static final int RALLY_TICKS = 600, RALLY_AMPLIFIER = 1;
	static final long RALLY_GAP = 200L;
	/** The mark of a sworn fighter. */
	static final String SWORN = "jaymoskingdoms.haven_sworn";
	private static final int LINES = 4;

	/** What a blood's sworn fighters add: health, damage, armour, knockback resistance, speed (a share), and the title they bear. */
	record Oath(double health, double damage, double armor, double knockback, double speed, String title) {
	}

	private static final Oath HARPOONER = new Oath(8.0, 3.0, 0.0, 0.1, 0.08, "harpooner");
	private static final Oath SHIELD_WARDEN = new Oath(14.0, 2.0, 4.0, 0.5, 0.0, "shield_warden");
	private static final Oath WARDEN = new Oath(6.0, 2.0, 0.0, 0.0, 0.1, "warden");
	private static final Oath SWORN_BLADE = new Oath(12.0, 5.0, 2.0, 0.2, 0.05, "sworn_blade");
	private static final Oath SWORN_GUARD = new Oath(6.0, 2.0, 0.0, 0.1, 0.0, "sworn_guard");

	private static final Identifier HEALTH_ID = JaymosKingdoms.id("haven_sworn_health"), DAMAGE_ID = JaymosKingdoms.id("haven_sworn_damage"),
		ARMOR_ID = JaymosKingdoms.id("haven_sworn_armor"), KNOCKBACK_ID = JaymosKingdoms.id("haven_sworn_knockback"),
		SPEED_ID = JaymosKingdoms.id("haven_sworn_speed");

	/** When the next fighter may be sworn in, by haven; when the sworn last rallied, by haven. */
	private static final Map<Integer, Long> NEXT_SWEAR = new HashMap<>(), RALLIED = new HashMap<>();
	/** (0.7.490) How far out each haven last looked for land, what it held then, and when it may look again regardless. */
	private static final Map<Integer, Looked> LOOKED = new HashMap<>();

	private record Looked(int radius, int held, long again) {
	}

	private HavenGrowth() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HavenGrowth::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> struck(entity, source));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			NEXT_SWEAR.clear();
			RALLIED.clear();
			LOOKED.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		boolean order = now % ORDER_TICKS == 0;
		for (Faction f : Factions.all(server)) {
			// (0.7.490) each haven thinks of land on its own tick, so they don't all look at once
			boolean claim = Math.floorMod(now + f.id(), CLAIM_TICKS) == 0;
			if (!order && !claim) {
				continue;
			}
			if (f.fallen() || f.type() == FactionType.BARBARIAN || !Havens.is(server, f) || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			if (order) {
				order(level, f, now);
			}
			if (claim) {
				grow(level, f, now);
			}
		}
	}

	// ---- works -------------------------------------------------------------------------------------------------------

	static boolean isWork(String entry) {
		return WORKS.contains(entry);
	}

	/** Whether haven {@code f} may build {@code entry}: any other building always; a work only by its blood (a mixed haven, all). */
	static boolean allows(MinecraftServer server, Faction f, String entry) {
		if (!isWork(entry)) {
			return true;
		}
		String haven = Havens.of(server, f);
		if (haven.isEmpty()) {
			return false;
		}
		if (Havens.MIXED.equals(haven)) {
			return true;
		}
		return switch (entry) {
			case HavenBuildings.FIELDS, HavenBuildings.WORKSHOP -> true;
			case HavenBuildings.MINE -> haven.equals(Heritage.Kind.STONEBORN.key()) || haven.equals(Heritage.Kind.WARRIORBORN.key());
			case HavenBuildings.DOCK -> haven.equals(Heritage.Kind.TIDEBORN.key());
			case HavenBuildings.MILL -> haven.equals(Heritage.Kind.SYLVAN.key());
			default -> false;
		};
	}

	/** The size {@code entry} is first built from. */
	private static int from(String entry) {
		return switch (entry) {
			case HavenBuildings.FIELDS -> FIELDS_FROM;
			case HavenBuildings.WORKSHOP -> WORKSHOP_FROM;
			case HavenBuildings.MINE -> MINE_FROM;
			case HavenBuildings.DOCK -> DOCK_FROM;
			default -> MILL_FROM;
		};
	}

	/** The most of {@code entry} a settlement of {@code type} ever raises. */
	static int cap(FactionType type, String entry) {
		int[] row = type == FactionType.KINGDOM ? KINGDOM_MOST : type == FactionType.FIEFDOM ? FIEFDOM_MOST : VILLAGE_MOST;
		return row[Math.max(0, WORKS.indexOf(entry))];
	}

	/** The most of {@code entry} {@code f} builds now ({@code usual} for anything but a work): none if its blood doesn't, else more as it grows. */
	static int max(MinecraftServer server, Faction f, String entry, int usual) {
		if (!isWork(entry)) {
			return usual;
		}
		if (!allows(server, f, entry)) {
			return 0;
		}
		return Math.max(0, Math.min(cap(f.type(), entry), 1 + (Planner.size(f) - from(entry)) / SIZE_PER_WORK));
	}

	/** How many of {@code entry} {@code f} has finished (the ones still going up make nothing yet). */
	static int built(Works.Site works, String entry) {
		int n = 0;
		for (Works.Built b : works.built()) {
			if (b.entry().equals(entry)) {
				n++;
			}
		}
		return n;
	}

	/** Works {@code f} has finished, of every kind. */
	static int works(MinecraftServer server, Faction f) {
		Works.Site site = Works.of(server, f.id());
		int n = 0;
		for (String w : WORKS) {
			n += built(site, w);
		}
		return n;
	}

	/** How many more uses a haven trader's wares last for its works ({@link HavenWares}). */
	static int stock(MinecraftServer server, Faction f) {
		return Math.min(STOCK_MOST, works(server, f) * STOCK_PER_WORK);
	}

	// ---- output ------------------------------------------------------------------------------------------------------

	/** The blood a work of haven {@code f} makes goods in today (a mixed haven's, a random one). */
	private static String blood(String haven, ServerLevel level) {
		if (!Havens.MIXED.equals(haven)) {
			return haven;
		}
		String[] four = {Heritage.Kind.TIDEBORN.key(), Heritage.Kind.STONEBORN.key(), Heritage.Kind.SYLVAN.key(), Heritage.Kind.WARRIORBORN.key()};
		return four[level.getRandom().nextInt(four.length)];
	}

	private static void add(Map<Item, Integer> goods, @Nullable Item item, int n) {
		if (item != null && n > 0) {
			goods.merge(item, n, Integer::sum);
		}
	}

	/** {@code f}'s works' goods for {@code days} days into its storage, and their trade income (from {@link Production#produce}). */
	static void produce(ServerLevel level, Faction f, int days) {
		MinecraftServer server = level.getServer();
		String haven = Havens.of(server, f);
		if (haven.isEmpty() || days <= 0) {
			return;
		}
		Works.Site site = Works.of(server, f.id());
		int fields = built(site, HavenBuildings.FIELDS), shops = built(site, HavenBuildings.WORKSHOP), mines = built(site, HavenBuildings.MINE),
			docks = built(site, HavenBuildings.DOCK), mills = built(site, HavenBuildings.MILL);
		if (fields + shops + mines + docks + mills == 0) {
			return;
		}
		var random = level.getRandom();
		Map<Item, Integer> goods = new LinkedHashMap<>();
		String blood = blood(haven, level);
		for (int i = 0; i < fields * FIELD_FOOD * days; i++) {
			Item[] pool = fieldGoods(blood);
			add(goods, pool[random.nextInt(pool.length)], 1);
		}
		for (int i = 0; i < shops * WORKSHOP_GOODS * days; i++) {
			Item[] pool = shopGoods(blood(haven, level));
			add(goods, pool[random.nextInt(pool.length)], 1);
		}
		for (int d = 0; d < days; d++) {
			if (shops > 0) {
				add(goods, shopWare(blood(haven, level), random), shops * WORKSHOP_WARES);
			}
			add(goods, HavenWares.CUT_GEM, mines * MINE_GEMS);
			add(goods, HavenWares.DEEP_FISH, docks * DOCK_FISH);
			if (docks > 0 && random.nextInt(PEARL_ONE_IN) == 0) {
				add(goods, HavenWares.TIDE_PEARL, docks);
			}
			add(goods, HavenWares.SYLVAN_HERBS, mills * MILL_HERBS);
		}
		int rock = mines * MINE_ROCK * days;
		add(goods, Items.COBBLESTONE, rock - rock / Production.STONE_ONE_IN);
		add(goods, Items.STONE, rock / Production.STONE_ONE_IN);
		for (int i = 0; i < mines * MINE_ORES * days; i++) {
			add(goods, Focus.ORES[random.nextInt(Focus.ORES.length)], 1);
		}
		for (int i = 0; i < docks * DOCK_CATCH * days; i++) {
			Item[] pool = {Items.COD, Items.SALMON, Items.DRIED_KELP, Items.KELP};
			add(goods, pool[random.nextInt(pool.length)], 1);
		}
		add(goods, Production.logItem(level, f), mills * MILL_LOGS * days);
		add(goods, Items.STICK, mills * days * 4);
		int lost = 0;
		for (Map.Entry<Item, Integer> e : goods.entrySet()) {
			lost += Storage.put(level, f, e.getKey(), e.getValue());
		}
		int income = (fields + shops + mines + docks + mills) * TRADE_INCOME * days;
		if (f.type().isRealm()) {
			Factions.update(server, f.id(), x -> x.withTreasury(x.treasury() + income));
		} else {
			lost += Storage.put(level, f, Items.EMERALD, income);
		}
		JaymosKingdoms.LOGGER.debug("{} works made {} kinds of goods, {} emeralds ({} days; {} lost for want of room)", f.name(), goods.size(), income, days, lost);
	}

	private static Item[] fieldGoods(String blood) {
		if (blood.equals(Heritage.Kind.TIDEBORN.key())) {
			return new Item[] {Items.DRIED_KELP, Items.BREAD, Items.WHEAT, Items.POTATO};
		}
		if (blood.equals(Heritage.Kind.SYLVAN.key())) {
			return new Item[] {Items.SWEET_BERRIES, Items.APPLE, Items.BREAD, Items.CARROT};
		}
		if (blood.equals(Heritage.Kind.WARRIORBORN.key())) {
			return new Item[] {Items.WHEAT, Items.BEEF, Items.MUTTON, Items.BREAD};
		}
		return new Item[] {Items.BREAD, Items.WHEAT, Items.CARROT, Items.POTATO, Items.BEETROOT};
	}

	private static Item[] shopGoods(String blood) {
		if (blood.equals(Heritage.Kind.TIDEBORN.key())) {
			return new Item[] {Items.STRING, Items.STRING, Items.DRIED_KELP, Items.COD};
		}
		if (blood.equals(Heritage.Kind.SYLVAN.key())) {
			return new Item[] {Items.ARROW, Items.ARROW, Items.STRING, Items.STICK};
		}
		if (blood.equals(Heritage.Kind.WARRIORBORN.key())) {
			return new Item[] {Items.IRON_INGOT, Items.LEATHER, Items.ARROW, Items.COAL};
		}
		return new Item[] {Items.IRON_INGOT, Items.IRON_INGOT, Items.COPPER_INGOT, Items.COAL};
	}

	/** The ware a workshop of {@code blood} turns out on top of its other goods (null: none today). */
	private static @Nullable Item shopWare(String blood, net.minecraft.util.RandomSource random) {
		if (blood.equals(Heritage.Kind.STONEBORN.key())) {
			return HavenWares.CUT_GEM;
		}
		if (blood.equals(Heritage.Kind.WARRIORBORN.key())) {
			return HavenWares.WAR_HIDE;
		}
		if (blood.equals(Heritage.Kind.SYLVAN.key())) {
			return HavenWares.SYLVAN_HERBS;
		}
		return blood.equals(Heritage.Kind.TIDEBORN.key()) && random.nextInt(PEARL_ONE_IN * 2) == 0 ? HavenWares.TIDE_PEARL : null;
	}

	// ---- trade (from Caravans) ---------------------------------------------------------------------------------------

	/** Goods a haven sends out beyond what it keeps: its output and wares, and food, ore and ingots; timber and rock only in plenty. */
	static boolean trades(ItemStack stack) {
		return !stack.isDamageableItem() && !stack.is(Items.EMERALD);
	}

	/** How much of a good a haven keeps back from its caravans: its building materials a good deal, the rest a little. */
	static int keeps(ItemStack stack) {
		if (stack.is(net.minecraft.tags.ItemTags.LOGS) || stack.is(net.minecraft.tags.ItemTags.PLANKS) || stack.is(Items.COBBLESTONE) || stack.is(Items.STONE)
			|| stack.is(Items.STICK)) {
			return HAVEN_KEEPS_BUILDING;
		}
		return Storage.isFood(stack) ? HAVEN_KEEPS_FOOD : HAVEN_KEEPS;
	}

	/** What it keeps of building materials, of food, of the rest. */
	static final int HAVEN_KEEPS_BUILDING = 256, HAVEN_KEEPS_FOOD = 48, HAVEN_KEEPS = 16;

	/** What one lot of a good fetches (emeralds) from the settlement that buys it, and the lot's size. */
	static long worth(Item item, int count) {
		ItemStack stack = new ItemStack(item);
		if (item == HavenWares.TIDE_PEARL || item == HavenWares.CUT_GEM || item == HavenWares.SYLVAN_HERBS || item == HavenWares.WAR_HIDE
			|| item == HavenWares.DEEP_FISH) {
			return (long)count * HavenWares.paid(item);
		}
		int per = stack.is(Items.IRON_INGOT) || stack.is(Items.COPPER_INGOT) || stack.is(Items.GOLD_INGOT) || stack.is(Items.DIAMOND) ? 4
			: Storage.isFood(stack) ? 12 : stack.is(Items.COBBLESTONE) || stack.is(net.minecraft.tags.ItemTags.LOGS) ? 32 : 16;
		return count / per;
	}

	/** How far a haven's caravan goes for a buyer (blocks). */
	static final double REACH = 800.0;

	/**
	 * The settlement haven {@code f} sends its caravan to: the richest of those within {@link #REACH} (by what its treasury or
	 * storehouse holds), nearer ones first on a tie; never one at war with it or fallen, a camp, or itself. Null if none.
	 */
	static @Nullable Faction partner(MinecraftServer server, Faction f) {
		Faction best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (Faction g : Factions.all(server)) {
			if (g.id() == f.id() || g.fallen() || g.type() == FactionType.BARBARIAN || Relations.atWar(f, g) || Relations.atWar(g, f)) {
				continue;
			}
			double d = Raids.horizontal(g.seat(), f.seat());
			if (d > REACH * REACH) {
				continue;
			}
			// a hub that buys is better than one that doesn't; a nearer one better than a far one
			double score = Math.min(500.0, g.treasury()) - Math.sqrt(d) * 0.5 + (g.type().isRealm() ? 100.0 : 0.0);
			if (score > bestScore) {
				best = g;
				bestScore = score;
			}
		}
		return best;
	}

	/** The goods a levy of haven {@code f}'s production is made of while its storehouse is out of reach (one entry per good, weighted by repeats). */
	static List<Item> levyGoods(MinecraftServer server, Faction f) {
		Works.Site site = Works.of(server, f.id());
		List<Item> goods = new ArrayList<>();
		String haven = Havens.of(server, f);
		for (int i = 0; i < built(site, HavenBuildings.FIELDS); i++) {
			goods.addAll(List.of(fieldGoods(haven)));
		}
		for (int i = 0; i < built(site, HavenBuildings.WORKSHOP); i++) {
			goods.addAll(List.of(shopGoods(haven)));
		}
		for (int i = 0; i < built(site, HavenBuildings.MINE); i++) {
			goods.addAll(List.of(Items.COBBLESTONE, Items.STONE, Items.COAL, Items.RAW_IRON, HavenWares.CUT_GEM));
		}
		for (int i = 0; i < built(site, HavenBuildings.DOCK); i++) {
			goods.addAll(List.of(Items.COD, Items.SALMON, HavenWares.DEEP_FISH, HavenWares.TIDE_PEARL));
		}
		for (int i = 0; i < built(site, HavenBuildings.MILL); i++) {
			goods.addAll(List.of(Items.STICK, HavenWares.SYLVAN_HERBS, Items.COAL));
		}
		return goods;
	}

	// ---- land --------------------------------------------------------------------------------------------------------

	/**
	 * The land {@code f}'s size calls for (chunks out from the seat), within the most its kind claims. (0.7.490) It counts
	 * rings out from the land the haven was founded with: a kingdom's ({@link KingdomEvents#CLAIM_RADIUS}), a fiefdom's
	 * ({@link GrimSites#FIEF_CLAIM}, or a kingdom's if it already holds land past what that and its size would give, as a
	 * kingdom sworn to an overlord does), a village's ({@link Villages#CLAIM_RADIUS}). The first ring comes at
	 * {@link #CLAIM_FROM}. The base is always one of those fixed founding sizes, never the land it holds now, so each claim
	 * does not lead straight on to the next.
	 */
	static int claimRadius(Faction f) {
		int size = Planner.size(f);
		int rings = size < CLAIM_FROM ? 0 : 1 + (size - CLAIM_FROM) / SIZE_PER_RING;
		int base, most;
		if (f.type() == FactionType.KINGDOM) {
			base = KingdomEvents.CLAIM_RADIUS;
			most = KINGDOM_CLAIM;
		} else if (f.type() == FactionType.FIEFDOM) {
			base = heldRadius(f) > GrimSites.FIEF_CLAIM + rings ? Math.max(GrimSites.FIEF_CLAIM, KingdomEvents.CLAIM_RADIUS) : GrimSites.FIEF_CLAIM;
			most = FIEFDOM_CLAIM;
		} else {
			base = Villages.CLAIM_RADIUS;
			most = VILLAGE_CLAIM;
		}
		return Math.min(Math.max(most, base), base + rings);
	}

	/** (0.7.490) How far out from its seat (chunks, the larger of the two axes) the furthest of {@code f}'s claims lies. */
	private static int heldRadius(Faction f) {
		ChunkPos middle = ChunkPos.containing(f.seat());
		int far = 0;
		for (long claim : f.claims()) {
			far = Math.max(far, Math.max(Math.abs(ChunkPos.getX(claim) - middle.x()), Math.abs(ChunkPos.getZ(claim) - middle.z())));
		}
		return far;
	}

	/**
	 * Haven {@code f} claims the land its size calls for that no one holds yet. A realm pays for it from its treasury; a village
	 * needs food in store. A village inside a realm's land (no claims of its own) is left as it is unless the whole of its seat
	 * chunk comes free. (0.7.490) It only looks again when its size calls for more land, it has lost land, or
	 * {@link #CLAIM_RECHECK} has passed; it retries after {@link #CLAIM_RETRY} if it couldn't pay.
	 */
	static void grow(ServerLevel level, Faction f, long now) {
		MinecraftServer server = level.getServer();
		if (!Economy.atPeace(server, f)) {
			return;
		}
		int radius = claimRadius(f), held = f.claims().size();
		Looked last = LOOKED.get(f.id());
		if (last != null && radius <= last.radius() && held >= last.held() && now < last.again()) {
			return;
		}
		List<Long> more = KingdomEvents.claim(server, f.seat(), radius);
		if (more.isEmpty() || f.claims().isEmpty() && !more.contains(ChunkPos.containing(f.seat()).pack())) {
			LOOKED.put(f.id(), new Looked(radius, held, now + CLAIM_RECHECK));
			return;
		}
		if (f.type().isRealm()) {
			long cost = (long)more.size() * CLAIM_COST;
			if (f.treasury() < cost) {
				LOOKED.put(f.id(), new Looked(radius, held, now + CLAIM_RETRY));
				return;
			}
			Factions.update(server, f.id(), x -> x.withTreasury(x.treasury() - cost));
		} else if (!f.holdings().storage().isEmpty() && (!Storage.loaded(level, f) || Storage.food(level, f) < CLAIM_FOOD)) {
			LOOKED.put(f.id(), new Looked(radius, held, now + CLAIM_RETRY));
			return;
		}
		LOOKED.put(f.id(), new Looked(radius, held + more.size(), now + CLAIM_RECHECK));
		Factions.update(server, f.id(), x -> {
			java.util.Set<Long> claims = new java.util.LinkedHashSet<>(x.claims());
			claims.addAll(more);
			return x.withClaims(List.copyOf(claims));
		});
		JaymosKingdoms.LOGGER.info("{} claims {} more chunks of land", f.name(), more.size());
	}

	// ---- the sworn order ---------------------------------------------------------------------------------------------

	/** The fighters {@code f} keeps sworn: more as it grows, within the most its kind keeps. */
	static int target(Faction f) {
		int most = f.type() == FactionType.KINGDOM ? KINGDOM_ORDER : f.type() == FactionType.FIEFDOM ? FIEFDOM_ORDER : VILLAGE_ORDER;
		Faction.Holdings h = f.holdings();
		int others = Math.max(0, Planner.size(f) - h.count(Folk.GUARD) - h.count(Folk.ARCHER));
		return Math.min(most, ORDER_BASE + others / FOLK_PER_SWORN);
	}

	private static boolean fighter(Worker w) {
		return w.role() == Folk.GUARD || w.role() == Folk.ARCHER;
	}

	/** The title and gifts of a sworn fighter of blood {@code kind} (null: no blood of the four). */
	static Oath oathOf(Heritage.@Nullable Kind kind) {
		if (kind == Heritage.Kind.TIDEBORN) {
			return HARPOONER;
		}
		if (kind == Heritage.Kind.STONEBORN) {
			return SHIELD_WARDEN;
		}
		if (kind == Heritage.Kind.SYLVAN) {
			return WARDEN;
		}
		return kind == Heritage.Kind.WARRIORBORN ? SWORN_BLADE : SWORN_GUARD;
	}

	/** Swears in those of the haven's guards and archers not yet sworn, sees their arms are right, and takes on one more if it's short. */
	static void order(ServerLevel level, Faction f, long now) {
		MinecraftServer server = level.getServer();
		AABB land = new AABB(f.seat()).inflate(Havens.LAND_RANGE);
		for (Worker w : Workers.near(level, land, x -> x.factionId() == f.id() && x.mob().isAlive() && fighter(x))) {
			swear(level, f, w);
		}
		Faction.Holdings h = f.holdings();
		int roster = h.count(Folk.GUARD) + h.count(Folk.ARCHER);
		if (roster >= target(f) || now < NEXT_SWEAR.getOrDefault(f.id(), 0L)) {
			return;
		}
		NEXT_SWEAR.put(f.id(), now + SWEAR_GAP);
		boolean paid = !h.storage().isEmpty();
		if (paid) {
			// the oath is paid in bread: it takes stores
			if (!Storage.loaded(level, f) || Storage.food(level, f) < OATH_FOOD_LEAST) {
				return;
			}
		}
		String haven = Havens.of(server, f);
		Heritage.Kind kind = Heritage.Kind.byKey(blood(haven, level));
		boolean archer = kind == Heritage.Kind.SYLVAN;
		Worker sworn = null;
		if (archer) {
			// wardens of the woods are bowmen
			sworn = Factions.keep(server, f.id()) != null ? KingdomEvents.spawnArcher(level, f) : KingdomEvents.spawn(level, f, Folk.ARCHER);
		} else {
			sworn = KingdomEvents.spawn(level, f, Folk.GUARD);
		}
		// (0.7.490) the bread is taken only once someone has joined (an MCA guard joins with none returned, so the roster tells)
		Faction after = Factions.get(server, f.id());
		int joined = after == null ? roster : after.holdings().count(Folk.GUARD) + after.holdings().count(Folk.ARCHER);
		if (sworn == null && joined <= roster) {
			return;
		}
		if (paid) {
			Storage.take(level, f, Storage::isFood, OATH_FOOD);
		}
		if (sworn != null) {
			swear(level, f, sworn);
		}
	}

	/** {@code w} takes the oath (once) and is armed in its blood's way then; (0.7.490) arms lost later are its own to win back (Rearm). */
	private static void swear(ServerLevel level, Faction f, Worker w) {
		PathfinderMob mob = w.mob();
		Heritage.Kind kind = VillagerHeritage.of(mob);
		if (kind == null || !Set.of(Heritage.Kind.TIDEBORN, Heritage.Kind.STONEBORN, Heritage.Kind.SYLVAN, Heritage.Kind.WARRIORBORN).contains(kind)) {
			kind = Heritage.Kind.byKey(Havens.of(level.getServer(), f));
		}
		Oath oath = oathOf(kind);
		if (!mob.entityTags().contains(SWORN)) {
			mob.addTag(SWORN);
			modifier(mob, Attributes.MAX_HEALTH, HEALTH_ID, oath.health(), AttributeModifier.Operation.ADD_VALUE);
			modifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, oath.damage(), AttributeModifier.Operation.ADD_VALUE);
			modifier(mob, Attributes.ARMOR, ARMOR_ID, oath.armor(), AttributeModifier.Operation.ADD_VALUE);
			modifier(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, oath.knockback(), AttributeModifier.Operation.ADD_VALUE);
			modifier(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, oath.speed(), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			mob.heal((float)oath.health());
			if (mob instanceof Folk folk && !mob.entityTags().contains(Caravans.TAG)) {
				// (0.7.490) armed once, at the oath: a weapon struck from its hand is not handed back for free
				arm(folk, kind);
			}
		}
		if (mob instanceof Folk folk && !mob.entityTags().contains(Caravans.TAG)) {
			if (folk.role() == Folk.GUARD || folk.role() == Folk.ARCHER) {
				folk.setCustomName(Component.translatable("entity.jaymoskingdoms.haven_guard." + oath.title(), f.name()));
			}
		}
	}

	private static void modifier(PathfinderMob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id, double amount,
		AttributeModifier.Operation op) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null && amount != 0.0 && !instance.hasModifier(id)) {
			instance.addPermanentModifier(new AttributeModifier(id, amount, op));
		}
	}

	/** A sworn fighter's arms by blood: harpoon and turtle-shell helm, shield, bow, axe. */
	private static void arm(Folk folk, Heritage.@Nullable Kind kind) {
		if (kind == Heritage.Kind.TIDEBORN && folk.role() == Folk.GUARD) {
			equip(folk, EquipmentSlot.MAINHAND, Items.TRIDENT);
			equip(folk, EquipmentSlot.HEAD, Items.TURTLE_HELMET);
		} else if (kind == Heritage.Kind.STONEBORN && folk.role() == Folk.GUARD) {
			equip(folk, EquipmentSlot.OFFHAND, Items.SHIELD);
		} else if (kind == Heritage.Kind.SYLVAN && folk.role() == Folk.ARCHER) {
			equip(folk, EquipmentSlot.MAINHAND, Items.BOW);
		} else if (kind == Heritage.Kind.WARRIORBORN && folk.role() == Folk.GUARD) {
			equip(folk, EquipmentSlot.MAINHAND, Items.IRON_AXE);
		}
	}

	private static void equip(Folk folk, EquipmentSlot slot, Item item) {
		if (!folk.getItemBySlot(slot).is(item)) {
			folk.setItemSlot(slot, new ItemStack(item));
			folk.setDropChance(slot, 0.0F);
		}
	}

	// ---- the rally ---------------------------------------------------------------------------------------------------

	/** The haven {@code e} is one of the people of (a fighter, a worker or a villager of it), or null. */
	private static @Nullable Faction resident(ServerLevel level, Entity e) {
		MinecraftServer server = level.getServer();
		Faction f = null;
		if (e instanceof Worker w && w.factionId() >= 0) {
			f = Factions.get(server, w.factionId());
		} else if (e instanceof Villager v) {
			f = Havens.home(v);
		}
		return f != null && !f.fallen() && Havens.is(server, f) ? f : null;
	}

	/** A person of a haven struck by an outsider (or one of its sworn fighters struck by anyone): the sworn near fall on him together. */
	private static void struck(LivingEntity victim, DamageSource source) {
		if (!(victim.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity attacker) || attacker == victim) {
			return;
		}
		Faction haven = resident(level, victim);
		if (haven == null || attacker instanceof Player p && (p.isCreative() || p.isSpectator())) {
			return;
		}
		Faction theirs = resident(level, attacker);
		if (theirs != null && theirs.id() == haven.id() || sanctioned(haven, attacker, victim)) {
			return;
		}
		long now = level.getGameTime();
		if (now < RALLIED.getOrDefault(haven.id(), 0L)) {
			return;
		}
		RALLIED.put(haven.id(), now + RALLY_GAP);
		rally(level, haven, victim, attacker);
	}

	/** (0.7.490) {@code sworn} took the haven's oath: it never runs. */
	static boolean sworn(Entity sworn) {
		return sworn.entityTags().contains(SWORN);
	}

	/** (0.7.490) A blow between {@code a} and {@code b} that is no attack on {@code haven}: an honour duel, a drill's spar, or one of its own sworn members. */
	private static boolean sanctioned(Faction haven, Entity a, Entity b) {
		if (com.jaymoskingdoms.forms.Duels.dueling(a, b) || com.jaymoskingdoms.forms.GuardDrills.sparring(a, b)) {
			return true;
		}
		return a instanceof Player p && haven.member(p.getUUID()) != null || b instanceof Player q && haven.member(q.getUUID()) != null;
	}

	/** The sworn of {@code haven} within reach of {@code at} go for {@code foe}, harder for it; the nearest sounds the horn and cries out. */
	static void rally(ServerLevel level, Faction haven, LivingEntity at, LivingEntity foe) {
		if (sanctioned(haven, foe, at)) {
			return;
		}
		List<Worker> sworn = Workers.near(level, at.getBoundingBox().inflate(RALLY_RANGE),
			x -> x.factionId() == haven.id() && x.mob().isAlive() && fighter(x) && x.mob() != foe && !Downed.isDowned(x.mob()));
		if (sworn.isEmpty()) {
			return;
		}
		PathfinderMob nearest = null;
		for (Worker w : sworn) {
			Rulers.charge(w.mob(), foe);
			w.mob().addEffect(new MobEffectInstance(MobEffects.STRENGTH, RALLY_TICKS, RALLY_AMPLIFIER, false, true));
			w.mob().addEffect(new MobEffectInstance(MobEffects.RESISTANCE, RALLY_TICKS, 0, false, true));
			if (nearest == null || w.mob().distanceToSqr(at) < nearest.distanceToSqr(at)) {
				nearest = w.mob();
			}
		}
		level.playSound(null, at.getX(), at.getY(), at.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 1.4F, 1.0F);
		if (nearest != null) {
			Speech.say(level, nearest, Component.translatable("speech.jaymoskingdoms.haven_guard.rally." + nearest.getRandom().nextInt(LINES)), Speech.RED, 32.0);
		}
		if (foe instanceof ServerPlayer player) {
			Reputation.change(player, haven, RALLY_REP);
		}
	}

	/** The name a player who strikes a haven's people loses with it, once a rally. */
	static final int RALLY_REP = -10;
}
