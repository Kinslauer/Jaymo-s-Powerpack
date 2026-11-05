package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Walled towns, Patch 5 of {@code docs/Hordes-Plan.md}: villages and Blossom villages tier up as the greenskin
 * camps do ({@link Hordes}): village, palisaded village, walled town, fortified town. Each tier past the first raises a new
 * outer wall round the settlement, one arc a day, never over anything a player placed or anything built; once it closes,
 * gates are cut in the old ring and roads run gate to gate; new buildings go into the band between the walls, laid out
 * by the same angle-slot planner ({@link Hordes#layout}), and the town claims the land it has grown over and takes in
 * newcomers up to its folk cap. Kingdoms and fiefdoms keep their open growth: nothing here touches them.
 *
 * <p>Each kind has its own wall, caps and rules ({@link Kind}). A village's wall is a log palisade in its land's own wood;
 * a village of a <i>richer kingdom</i> ({@link Wealth}: its overlord realm, or the realm nearest, prosperous or better when
 * the wall goes up) raises it in stone instead. A Blossom village walls in its own way: dark timber posts, white
 * plaster over a stone footing, capped with dark tile, a lantern on every post. It pays for it all out of its own purse,
 * which fills each day from its folk (more in a rich realm); it climbs a tier only with the folk, the buildings, the age
 * and the purse for it.
 *
 * <p>Saved in {@link #TOWNS} (settlement id to {@link Town}); a village with no entry is tier 1. Nothing goes up unless the
 * settlement is loaded and no player is near, and only one thing a look.
 */
final class WalledTowns {
	// ================================================================================================================
	// Tuning
	/** How often (ticks) the towns are looked at: the day's accounts when a new day has come, and one thing built. */
	static final int CHECK_TICKS = 200;
	/** Each kind's wall radius by tier. Tier 1 has no wall: its number is where the first band begins. */
	static final int[] VILLAGE_WALL = {18, 48, 64, 80}, BLOSSOM_WALL = {BlossomVillages.RADIUS, 40, 54, 68};
	/** Each kind's folk cap by tier (villagers; 40 is the top, as the greenskins'). */
	static final int[] VILLAGE_FOLK = {14, 24, 32, 40}, BLOSSOM_FOLK = {10, 18, 26, 34};
	/** Folk it needs to leave each tier (tiers 1 to 3), buildings it must have raised, and days of age to reach each tier. */
	static final int[] VILLAGE_POP = {10, 18, 26, 0}, BLOSSOM_POP = {8, 14, 22, 0};
	static final int[] VILLAGE_BUILDINGS = {1, 0, 0, 0}, BLOSSOM_BUILDINGS = {9, 0, 0, 0};
	static final int[] VILLAGE_AGE = {0, 8, 20, 40}, BLOSSOM_AGE = {0, 10, 24, 44};
	/** What the climb to each tier costs the purse (index: the tier it leaves), a new arc of wall, and a band building. */
	static final int[] VILLAGE_COST = {40, 100, 200, 0}, BLOSSOM_COST = {50, 120, 220, 0};
	static final int ARC_COST = 2, BUILD_COST = 4;
	/** The purse's daily income: this much a villager, more under a rich realm (percent added per {@link Wealth.Tier} step). */
	static final int INCOME_PER_FOLK = 1, INCOME_RICH_PERCENT = 50;
	/** The least realm wealth tier 4 asks of a village (a Blossom village is its own lord), and the wealth a stone wall asks. */
	static final Wealth.Tier TOP_TIER_WEALTH = Wealth.Tier.PROSPEROUS, STONE_WEALTH = Wealth.Tier.PROSPEROUS;
	/** How far (blocks) the nearest realm is looked for when a village has no overlord. */
	static final double REALM_RANGE = 1200.0;
	/** Land by tier: chunks out each way from the seat. */
	static final int[] CLAIM_RADIUS = {Villages.CLAIM_RADIUS, 3, 4, 5};
	/** A new wall goes up in this many arcs, one a day. */
	static final int ARCS = 8;
	/** Wall height, post height, walkway height, gate half-width, tower height, and the arc length between posts. */
	static final int WALL_HIGH = 6, POST_HIGH = 7, WALK_HIGH = 3, GATE_HALF = 2, TOWER_HIGH = 9, POST_SPACING = 10;
	/** Iron golems wanted at each tier (the sworn defenders of a walled town), and newcomers a day at most (plus one a tier). */
	static final int[] GOLEMS = {0, 1, 2, 3};
	static final int NEWCOMERS = 1;
	/** Nothing goes up with a player this near. */
	static final double BUILD_HIDE_RANGE = 24.0;
	/** The band: buildings stay this far inside the new wall and outside the old. */
	static final int BAND_INNER = 3, BAND_OUTER = 2;
	/** Roads are this half-width. */
	static final int ROAD_HALF = 1;
	/** (0.7.490) Folk and golems are counted this far past the town's wall radius. */
	static final int HOME_MARGIN = 4;
	/** (0.7.490) The settlement's own builders ({@link Planner}) keep this far inside a walled town's newest wall. */
	static final int PLAN_MARGIN = 4;
	// ================================================================================================================

	/** Which way a settlement builds. */
	enum Kind {
		VILLAGE("village", VILLAGE_WALL, VILLAGE_FOLK, VILLAGE_POP, VILLAGE_BUILDINGS, VILLAGE_AGE, VILLAGE_COST),
		BLOSSOM("blossom", BLOSSOM_WALL, BLOSSOM_FOLK, BLOSSOM_POP, BLOSSOM_BUILDINGS, BLOSSOM_AGE, BLOSSOM_COST);

		final String id;
		final int[] wall, folk, pop, buildings, age, cost;

		Kind(String id, int[] wall, int[] folk, int[] pop, int[] buildings, int[] age, int[] cost) {
			this.id = id;
			this.wall = wall;
			this.folk = folk;
			this.pop = pop;
			this.buildings = buildings;
			this.age = age;
			this.cost = cost;
		}

		int tiers() {
			return this.wall.length;
		}
	}

	/**
	 * A town's tier and its progress in it: {@code since} (the day it reached the tier), the arcs of the tier's wall raised,
	 * the band buildings raised (or skipped), whether the old ring has its gates and roads, whether this tier's wall is
	 * stone, its {@code purse}, {@code born} (the day it was first seen), {@code lastDay} (the last day its accounts were
	 * done), {@code folk} (the most villagers last seen at home) and {@code gate} (the way its front looks: a horizontal
	 * data value 0 to 3, or -1 for none chosen yet).
	 */
	record Town(int tier, long since, int arcs, int built, boolean opened, boolean stone, long purse, long born, long lastDay, int folk, int gate) {
		static final Town START = new Town(1, 0L, ARCS, 0, true, false, 0L, -1L, -1L, 0, -1);
		static final Codec<Town> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("tier", 1).forGetter(Town::tier),
				Codec.LONG.optionalFieldOf("since", 0L).forGetter(Town::since),
				Codec.INT.optionalFieldOf("arcs", ARCS).forGetter(Town::arcs),
				Codec.INT.optionalFieldOf("built", 0).forGetter(Town::built),
				Codec.BOOL.optionalFieldOf("opened", true).forGetter(Town::opened),
				Codec.BOOL.optionalFieldOf("stone", false).forGetter(Town::stone),
				Codec.LONG.optionalFieldOf("purse", 0L).forGetter(Town::purse),
				Codec.LONG.optionalFieldOf("born", -1L).forGetter(Town::born),
				Codec.LONG.optionalFieldOf("last_day", -1L).forGetter(Town::lastDay),
				Codec.INT.optionalFieldOf("folk", 0).forGetter(Town::folk),
				Codec.INT.optionalFieldOf("gate", -1).forGetter(Town::gate))
			.apply(i, Town::new));

		Town withArcs(int arcs) {
			return new Town(this.tier, this.since, arcs, this.built, this.opened, this.stone, this.purse, this.born, this.lastDay, this.folk, this.gate);
		}

		Town withBuilt(int built) {
			return new Town(this.tier, this.since, this.arcs, built, this.opened, this.stone, this.purse, this.born, this.lastDay, this.folk, this.gate);
		}

		Town withOpened() {
			return new Town(this.tier, this.since, this.arcs, this.built, true, this.stone, this.purse, this.born, this.lastDay, this.folk, this.gate);
		}

		Town withPurse(long purse) {
			return new Town(this.tier, this.since, this.arcs, this.built, this.opened, this.stone, Math.max(0L, purse), this.born, this.lastDay, this.folk, this.gate);
		}

		Town withDay(long born, long lastDay, int folk) {
			return new Town(this.tier, this.since, this.arcs, this.built, this.opened, this.stone, this.purse, born, lastDay, folk, this.gate);
		}

		Town withGate(int gate) {
			return new Town(this.tier, this.since, this.arcs, this.built, this.opened, this.stone, this.purse, this.born, this.lastDay, this.folk, gate);
		}
	}

	/** On the Overworld: every village's tier (settlement id to it). Saved. */
	static final AttachmentType<Map<String, Town>> TOWNS = AttachmentRegistry.create(JaymosKingdoms.id("walled_towns"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Town.CODEC)));

	/**
	 * (0.7.490) On the Overworld: the footprint of every band building a town has raised, every tier (settlement id to its
	 * footprints, four numbers each: min x, min z, max x, max z). The builders ({@link Planner#taken}) keep off them, and
	 * never take them for old ruins to tear down. Saved.
	 */
	static final AttachmentType<Map<String, List<Integer>>> RAISED = AttachmentRegistry.create(JaymosKingdoms.id("walled_town_band"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT.listOf())));

	/** A planned band: the tier it's for, and its buildings in the order they go up (town frame). */
	private record Plan(int tier, List<IllagerBuildings.Spot> spots) {
	}

	private static final Map<Integer, Plan> PLANS = new HashMap<>();
	/** Piece id to its design, drawn once (so its footprint is the same at planning and at raising). */
	private static final Map<String, Blueprint> DESIGNS = new HashMap<>();
	/** Settlement id to whether it is a Blossom village (its land never changes). */
	private static final Map<Integer, Boolean> BLOSSOMS = new HashMap<>();

	private WalledTowns() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WalledTowns::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PLANS.clear();
			DESIGNS.clear();
			BLOSSOMS.clear();
		});
	}

	// ---- state ---------------------------------------------------------------------------------------------------------

	static Town town(MinecraftServer server, int id) {
		return Factions.level(server).getAttachedOrElse(TOWNS, Map.of()).getOrDefault(Integer.toString(id), Town.START);
	}

	/** (0.7.530) A finished wall project ({@link TownProjects}) adds {@code add} to a village's purse, toward its next wall tier. */
	static void fund(MinecraftServer server, Faction f, long add) {
		if (f.type() == FactionType.VILLAGE) {
			Town t = town(server, f.id());
			put(server, f.id(), t.withPurse(t.purse() + add));
		}
	}

	private static void put(MinecraftServer server, int id, Town town) {
		Map<String, Town> map = new HashMap<>(Factions.level(server).getAttachedOrElse(TOWNS, Map.of()));
		map.put(Integer.toString(id), town);
		Factions.level(server).setAttached(TOWNS, Map.copyOf(map));
	}

	/** (0.7.490) Every band building's footprint, every town: {minX, minZ, maxX, maxZ}. */
	static List<int[]> raised(MinecraftServer server) {
		List<int[]> out = new ArrayList<>();
		for (List<Integer> all : Factions.level(server).getAttachedOrElse(RAISED, Map.of()).values()) {
			for (int i = 0; i + 3 < all.size(); i += 4) {
				out.add(new int[] {all.get(i), all.get(i + 1), all.get(i + 2), all.get(i + 3)});
			}
		}
		return out;
	}

	/** (0.7.490) Notes a band building of town {@code id} stands over {@code minX..maxX, minZ..maxZ}. */
	private static void raised(MinecraftServer server, int id, int minX, int minZ, int maxX, int maxZ) {
		Map<String, List<Integer>> map = new HashMap<>(Factions.level(server).getAttachedOrElse(RAISED, Map.of()));
		List<Integer> list = new ArrayList<>(map.getOrDefault(Integer.toString(id), List.of()));
		list.addAll(List.of(minX, minZ, maxX, maxZ));
		map.put(Integer.toString(id), List.copyOf(list));
		Factions.level(server).setAttached(RAISED, Map.copyOf(map));
	}

	/** Village {@code id}'s tier (1 for any other settlement, or a village that hasn't grown). */
	static int tier(MinecraftServer server, int id) {
		return town(server, id).tier();
	}

	/** {@code f} is a village this class grows. (0.7.490) Never a haven: havens only trade, and grow for their output. */
	static boolean grows(MinecraftServer server, Faction f) {
		return f.type() == FactionType.VILLAGE && !f.fallen() && !Havens.is(server, f);
	}

	/**
	 * Which way {@code f} builds. (0.7.490) A Blossom village is known by its saved front ({@link Town#gate}, set only
	 * when one rises), so one founded outside the cherry groves stays one after a restart; the land decides the rest.
	 */
	static Kind kind(ServerLevel level, Faction f) {
		return BLOSSOMS.computeIfAbsent(f.id(), x -> town(level.getServer(), x).gate() >= 0 || BlossomVillages.blossom(level, f))
			? Kind.BLOSSOM : Kind.VILLAGE;
	}

	/** (0.7.490) Where a town's folk and golems are counted: the bell's box, or out to its own wall, whichever is wider. */
	private static AABB home(Faction f, Kind kind, int tier) {
		return new AABB(f.seat()).inflate(Math.max(Villages.VILLAGE_RADIUS, kind.wall[tier - 1] + HOME_MARGIN));
	}

	/** (0.7.490) How far round its seat village {@code f}'s folk and golems are counted ({@link Villages}): out to its wall. */
	static double reach(ServerLevel level, Faction f) {
		MinecraftServer server = level.getServer();
		if (!grows(server, f)) {
			return Villages.VILLAGE_RADIUS;
		}
		int tier = tier(server, f.id());
		return tier < 2 ? Villages.VILLAGE_RADIUS : Math.max(Villages.VILLAGE_RADIUS, kind(level, f).wall[tier - 1] + HOME_MARGIN);
	}

	/** (0.7.490) Village {@code f} has its tier's folk: no newcomer moves into a new house, however many beds stand spare. */
	static boolean full(ServerLevel level, Faction f) {
		MinecraftServer server = level.getServer();
		if (!grows(server, f)) {
			return false;
		}
		int tier = tier(server, f.id());
		Kind kind = kind(level, f);
		return level.getEntitiesOfClass(Villager.class, home(f, kind, tier), Villager::isAlive).size() >= kind.folk[tier - 1];
	}

	/** (0.7.490) Village {@code f} has climbed past tier 1: its own rings wall it in, not the builders' rectangle ({@link Enclosure}). */
	static boolean walled(MinecraftServer server, Faction f) {
		return grows(server, f) && tier(server, f.id()) >= 2;
	}

	/**
	 * (0.7.490) Whether the builders ({@link Planner}) may put up a building over {@code minX..maxX, minZ..maxZ}: well
	 * inside a walled town's newest wall, and on none of its band's planned plots (raised or still to rise: the raised ones,
	 * and earlier tiers', are in {@link Planner#taken} too). True for any other place.
	 */
	static boolean plannable(ServerLevel level, Faction f, int minX, int minZ, int maxX, int maxZ) {
		MinecraftServer server = level.getServer();
		if (!walled(server, f)) {
			return true;
		}
		double most = radius(level, f) - PLAN_MARGIN;
		BlockPos seat = f.seat();
		for (int x : new int[] {minX, maxX}) {
			for (int z : new int[] {minZ, maxZ}) {
				double dx = x + 0.5 - seat.getX(), dz = z + 0.5 - seat.getZ();
				if (dx * dx + dz * dz > most * most) {
					return false;
				}
			}
		}
		Town t = town(server, f.id());
		List<IllagerBuildings.Spot> band = plan(level, f, kind(level, f), t.tier());
		// (0.7.490) every plot of the band, the raised ones too: a building begun in a raised one's place would interleave with it
		for (IllagerBuildings.Spot spot : band) {
			CampBuildings.Placer flat = spot.placer(seat, gateOf(t, f));
			int[] b = Hordes.bounds(new Hordes.Piece(spot.id(), spot.design()));
			BlockPos a = flat.at(b[0], 0, b[1]), c = flat.at(b[2], 0, b[3]);
			if (maxX >= Math.min(a.getX(), c.getX()) && minX <= Math.max(a.getX(), c.getX()) && maxZ >= Math.min(a.getZ(), c.getZ())
				&& minZ <= Math.max(a.getZ(), c.getZ())) {
				return false;
			}
		}
		return true;
	}

	/** Its folk cap at its tier. */
	static int folkCap(ServerLevel level, Faction f) {
		return kind(level, f).folk[tier(level.getServer(), f.id()) - 1];
	}

	/** The wall radius it has built round itself so far, or is building (0 at tier 1: no wall). */
	static int radius(ServerLevel level, Faction f) {
		int tier = tier(level.getServer(), f.id());
		return tier < 2 ? 0 : kind(level, f).wall[tier - 1];
	}

	/** The lang key of village {@code f}'s tier name ("Walled town"...), or null for any other faction. */
	static @Nullable String tierKey(MinecraftServer server, Faction f) {
		if (f.type() != FactionType.VILLAGE || Havens.is(server, f)) {
			return null;
		}
		return "camp.jaymoskingdoms.tier." + (kind(Factions.level(server), f) == Kind.BLOSSOM ? "blossom." : "village.") + tier(server, f.id());
	}

	/** Remembers which way a Blossom village's torii faces (its front), when it rises. */
	static void front(MinecraftServer server, int id, Direction gate) {
		put(server, id, town(server, id).withGate(gate.get2DDataValue()));
		BLOSSOMS.put(id, true);
	}

	private static Direction gateOf(Town t, Faction f) {
		return Direction.from2DDataValue(t.gate() >= 0 ? t.gate() : Math.floorMod(f.id() * 31 + 7, 4));
	}

	// ---- the realm: wealth -----------------------------------------------------------------------------------------------

	/** The kingdom whose wealth a village shares: its overlord's chain up to a kingdom, or the nearest realm's. */
	static @Nullable Faction realm(MinecraftServer server, Faction f) {
		Faction x = f.overlord() >= 0 ? Factions.get(server, f.overlord()) : nearestRealm(server, f.seat());
		for (int i = 0; i < 4 && x != null; i++) {
			if (x.type() == FactionType.KINGDOM) {
				return x;
			}
			x = x.overlord() >= 0 ? Factions.get(server, x.overlord()) : null;
		}
		return null;
	}

	/** The closest standing realm within REALM_RANGE of a free village, by distance, not list order (0.7.490). */
	private static @Nullable Faction nearestRealm(MinecraftServer server, BlockPos pos) {
		Faction best = null;
		double bestDist = REALM_RANGE * REALM_RANGE;
		for (Faction r : Factions.all(server)) {
			if (!r.type().isRealm() || r.fallen()) {
				continue;
			}
			double d = r.seat().distSqr(pos);
			if (d <= bestDist) {
				best = r;
				bestDist = d;
			}
		}
		return best;
	}

	static Wealth.Tier wealth(MinecraftServer server, Faction f) {
		Faction realm = realm(server, f);
		return realm == null ? Wealth.Tier.POOR : Wealth.of(server, realm);
	}

	// ---- the tick ----------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long time = level.getGameTime();
		if (time % CHECK_TICKS != 11) {
			return;
		}
		long today = time / Camps.DAY_TICKS;
		for (Faction f : Factions.all(server, FactionType.VILLAGE)) {
			if (!grows(server, f)) {
				continue;
			}
			Town t = town(server, f.id());
			if (t.lastDay() != today) {
				day(level, f, today);
			}
			Faction fresh = Factions.get(server, f.id());
			if (fresh != null && level.isLoaded(fresh.seat())) {
				step(level, fresh, false);
			}
		}
	}

	/** A village's day: its purse filled from its folk, its newcomers and golems, and a tier up if it's ready. */
	private static void day(ServerLevel level, Faction f, long today) {
		MinecraftServer server = level.getServer();
		Town t = town(server, f.id());
		Kind kind = kind(level, f);
		boolean loaded = level.isPositionEntityTicking(f.seat());
		int folk = Math.max(t.folk(), f.holdings().population());
		if (loaded) {
			folk = level.getEntitiesOfClass(Villager.class, home(f, kind, t.tier()), Villager::isAlive).size();
		}
		long born = t.born() < 0 ? today : t.born();
		long rich = 100 + (long)INCOME_RICH_PERCENT * wealth(server, f).ordinal();
		long income = (long)folk * INCOME_PER_FOLK * rich / 100L;
		put(server, f.id(), t.withDay(born, today, folk).withPurse(t.purse() + income));
		if (loaded && t.tier() >= 2) {
			settle(level, f, kind, folk);
		}
		Faction now = Factions.get(server, f.id());
		Town h = town(server, f.id());
		if (now != null && h.tier() < kind.tiers() && missing(level, now, h, kind).isEmpty()) {
			promote(level, now, h.withPurse(h.purse() - kind.cost[h.tier() - 1]), true);
		}
	}

	/** What village {@code f} still lacks for its next tier (the command shows it); empty when it's ready. */
	static List<String> missing(ServerLevel level, Faction f, Town t, Kind kind) {
		MinecraftServer server = level.getServer();
		List<String> out = new ArrayList<>();
		int tier = t.tier();
		if (tier >= kind.tiers()) {
			out.add("already at the top tier");
			return out;
		}
		int folk = Math.max(t.folk(), f.holdings().population());
		if (folk < kind.pop[tier - 1]) {
			out.add("folk " + folk + "/" + kind.pop[tier - 1]);
		}
		if (tier == 1) {
			int buildings = Works.of(server, f.id()).built().size();
			if (buildings < kind.buildings[0]) {
				out.add("buildings " + buildings + "/" + kind.buildings[0]);
			}
		} else {
			if (t.arcs() < ARCS) {
				out.add("wall " + t.arcs() + "/" + ARCS + " arcs");
			} else if (!t.opened()) {
				out.add("gates and roads not yet cut");
			}
			// (0.7.490) the band's designs are drawn only while the town is loaded (an unloaded one reads as bare land, and its
			// designs and plan would keep default stuff for the session): till then it waits, its band not yet counted
			List<IllagerBuildings.Spot> band = planned(level, f, kind, tier);
			if (band == null) {
				out.add("band buildings " + t.built() + "/? (town not loaded)");
			} else if (t.built() < band.size()) {
				out.add("band buildings " + t.built() + "/" + band.size());
			}
		}
		long age = Math.max(0L, level.getGameTime() / Camps.DAY_TICKS - Math.max(0L, t.born()));
		if (age < kind.age[tier]) {
			out.add("age " + age + "/" + kind.age[tier] + " days");
		}
		if (t.purse() < kind.cost[tier - 1]) {
			out.add("purse " + t.purse() + "/" + kind.cost[tier - 1]);
		}
		if (kind == Kind.VILLAGE && tier + 1 == kind.tiers() && wealth(server, f).ordinal() < TOP_TIER_WEALTH.ordinal()) {
			out.add("a realm of " + TOP_TIER_WEALTH.key() + " wealth behind it");
		}
		return out;
	}

	/** Moves village {@code f} up to {@code t}'s tier plus one: its land claimed, its wall and band begun, the news told. */
	static void promote(ServerLevel level, Faction f, Town t, boolean tell) {
		MinecraftServer server = level.getServer();
		Kind kind = kind(level, f);
		int next = t.tier() + 1;
		if (next > kind.tiers()) {
			return;
		}
		// (the wall goes up in stone only in a richer kingdom's village; a Blossom village has its own way)
		boolean stone = kind == Kind.VILLAGE && wealth(server, f).ordinal() >= STONE_WEALTH.ordinal();
		long today = level.getGameTime() / Camps.DAY_TICKS;
		put(server, f.id(), new Town(next, today, 0, 0, false, stone, t.purse(), t.born(), t.lastDay(), t.folk(), t.gate()));
		PLANS.remove(f.id());
		List<Long> more = KingdomEvents.claim(server, f.seat(), CLAIM_RADIUS[next - 1]);
		Factions.update(server, f.id(), x -> {
			Set<Long> claims = new LinkedHashSet<>(x.claims());
			claims.addAll(more);
			return x.withClaims(List.copyOf(claims));
		});
		if (tell) {
			Component tierName = Component.translatable("camp.jaymoskingdoms.tier." + kind.id + "." + next);
			Component message = Component.translatable("message.jaymoskingdoms.town." + kind.id + "." + next, f.name(), tierName)
				.withStyle(ChatFormatting.GOLD);
			if (next >= 3) {
				KingdomEvents.announce(server, message);
			} else {
				for (ServerPlayer player : level.players()) {
					if (player.blockPosition().distSqr(f.seat()) < 192.0 * 192.0) {
						player.sendSystemMessage(message);
					}
				}
			}
		}
		JaymosKingdoms.LOGGER.info("{} grew to tier {}", f.name(), next);
	}

	/** Newcomers move in up to the cap, and golems stand sworn at the gate. */
	private static void settle(ServerLevel level, Faction f, Kind kind, int folk) {
		Town t = town(level.getServer(), f.id());
		int cap = kind.folk[t.tier() - 1];
		int arrivals = Math.min(cap - folk, NEWCOMERS + t.tier() - 1);
		CampBuildings.Placer frame = new CampBuildings.Placer(f.seat(), gateOf(t, f), 0);
		int r = kind.wall[t.tier() - 1];
		var random = level.getRandom();
		for (int i = 0; i < arrivals; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double d = 4.0 + random.nextDouble() * Math.max(4.0, r - 10.0);
			BlockPos at = frame.at((int)Math.round(Math.sin(a) * d), 0, (int)Math.round(-Math.cos(a) * d));
			if (!level.isLoaded(at) || Raids.playerNear(level, at, BUILD_HIDE_RANGE)) {
				continue;
			}
			EntityTypes.VILLAGER.spawn(level, Construction.standable(level, at, 4), EntitySpawnReason.STRUCTURE);
		}
		// (0.7.490) counted over the whole town, so the golem at the gate and newcomers out by the wall are seen
		int golems = level.getEntitiesOfClass(IronGolem.class, home(f, kind, t.tier()), IronGolem::isAlive).size();
		BlockPos gate = frame.at(0, 0, -(r - 5));
		if (golems < GOLEMS[t.tier() - 1] && level.isLoaded(gate) && !Raids.playerNear(level, gate, BUILD_HIDE_RANGE)) {
			EntityTypes.IRON_GOLEM.spawn(level, Construction.standable(level, gate, 4), EntitySpawnReason.STRUCTURE);
		}
	}

	// ---- building, each look at a loaded village ------------------------------------------------------------------------------

	/**
	 * One step of the tier's building: the next arc of the wall, the gates and roads once it closes, or the next band
	 * building, as the days since it reached its tier allow and its purse pays. {@code all}: whatever the days, the cost
	 * or who's near (the operator's command). Returns whether something went up.
	 */
	private static boolean step(ServerLevel level, Faction f, boolean all) {
		MinecraftServer server = level.getServer();
		Town t = town(server, f.id());
		Kind kind = kind(level, f);
		if (t.tier() < 2 || !all && !level.isLoaded(f.seat())) {
			return false;
		}
		long days = Math.max(0L, level.getGameTime() / Camps.DAY_TICKS - t.since());
		int arcs = all ? ARCS : (int)Math.min(ARCS, days);
		Look look = look(level, f, kind, t);
		if (t.arcs() < arcs) {
			if (!all && t.purse() < ARC_COST) {
				return false;
			}
			if (arc(level, f, kind, t, look, all)) {
				put(server, f.id(), t.withArcs(t.arcs() + 1).withPurse(all ? t.purse() : t.purse() - ARC_COST));
				return true;
			}
			return false;
		}
		if (t.arcs() >= ARCS && !t.opened()) {
			if (open(level, f, kind, t, look)) {
				put(server, f.id(), t.withOpened());
				return true;
			}
			return false;
		}
		List<IllagerBuildings.Spot> plan = plan(level, f, kind, t.tier());
		int allowed = all ? plan.size() : (int)Math.min(plan.size(), days);
		if (t.built() < allowed && (all || t.purse() >= BUILD_COST)) {
			Raise r = raise(level, f, t, plan.get(t.built()), all);
			if (r != Raise.WAIT) {
				put(server, f.id(), t.withBuilt(t.built() + 1).withPurse(all || r == Raise.SKIPPED ? t.purse() : t.purse() - BUILD_COST));
				return true;
			}
		}
		return false;
	}

	/** Builds the rest of the tier at once (the operator's command). */
	private static void complete(ServerLevel level, int id) {
		for (int i = 0; i < 400; i++) {
			Faction f = Factions.get(level.getServer(), id);
			if (f == null || !step(level, f, true)) {
				return;
			}
		}
	}

	// ---- the look: each kind's wall ------------------------------------------------------------------------------------------

	/** What a town's wall is made of. */
	private record Look(Kind kind, boolean stone, Palette land, BlockState post, BlockState beam, BlockState walk, BlockState stake, Set<Block> parts) {
		/** The wall's block at height {@code y} of the column at town-frame {@code (x, z)}. */
		BlockState fill(int x, int y, int z) {
			if (this.kind == Kind.BLOSSOM) {
				// dark timber beam band under the cap, white plaster over a stone footing
				return y < 2 ? Blocks.STONE_BRICKS.defaultBlockState() : y == WALL_HIGH - 1 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : plaster();
			}
			if (!this.stone) {
				return this.post;
			}
			int h = Math.floorMod(x * 73428767 ^ y * 912931 ^ z * 42317861, 7);
			BlockState s = switch (h) {
				case 0, 1, 2 -> this.land.wall();
				case 3, 4 -> this.land.mossy();
				case 5 -> this.land.cracked();
				default -> Blocks.COBBLESTONE.defaultBlockState();
			};
			// (0.7.490) none of the land's own stone in the wall, so a gate cut tells the wall from the ground
			if (earthy(s)) {
				for (BlockState other : new BlockState[] {this.land.wall(), this.land.mossy(), this.land.cracked()}) {
					if (!earthy(other)) {
						return other;
					}
				}
				return Blocks.COBBLESTONE.defaultBlockState();
			}
			return s;
		}

		/** The block over a gate's lintel. */
		BlockState gateTop() {
			return this.kind == Kind.BLOSSOM ? tile() : this.stake;
		}

		boolean part(BlockState s) {
			return this.parts.contains(s.getBlock());
		}
	}

	/** (0.7.490) {@code s} is a block the land itself is made of (stone, sandstone, terracotta): never part of a wall. */
	private static boolean earthy(BlockState s) {
		return s.is(Blocks.STONE) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.TERRACOTTA);
	}

	private static BlockState plaster() {
		return CampBuildings.named("white_terracotta");
	}

	private static BlockState tile() {
		return Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState();
	}

	private static Look look(ServerLevel level, Faction f, Kind kind, Town t) {
		Palette land = Palette.at(level, f.seat());
		Set<Block> parts = new HashSet<>();
		BlockState post, beam, walk, stake;
		if (kind == Kind.BLOSSOM) {
			post = CampBuildings.named("stripped_dark_oak_log");
			beam = Blocks.DARK_OAK_LOG.defaultBlockState();
			walk = Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
			stake = Blocks.LANTERN.defaultBlockState();
			for (BlockState s : new BlockState[] {post, beam, walk, Blocks.STONE_BRICKS.defaultBlockState(), Blocks.DARK_OAK_PLANKS.defaultBlockState(),
				plaster(), tile(), Blocks.LANTERN.defaultBlockState(), Blocks.DEEPSLATE_TILES.defaultBlockState(), Blocks.LADDER.defaultBlockState()}) {
				parts.add(s.getBlock());
			}
		} else {
			post = land.log();
			beam = land.log();
			// (0.7.490) the walkway is timber in a stone wall too, so ladders and gate towers find the ground through it
			walk = land.woodSlab();
			if (walk.hasProperty(BlockStateProperties.SLAB_TYPE)) {
				walk = walk.setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
			}
			stake = t.stone() ? Blocks.STONE_BRICK_WALL.defaultBlockState() : land.fence();
			// both the log and the stone wall's parts, so a stone town's gates cut through its old log ring too
			for (BlockState s : new BlockState[] {land.log(), land.fence(), land.woodSlab(), land.slab(), land.wall(), land.mossy(), land.cracked(),
				Blocks.COBBLESTONE.defaultBlockState(), Blocks.STONE_BRICK_WALL.defaultBlockState(), Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState(),
				Blocks.LADDER.defaultBlockState(), land.planks()}) {
				// (0.7.490) never the land's own stone, sandstone or terracotta: a gate cut would dig into the ground
				if (!earthy(s)) {
					parts.add(s.getBlock());
				}
			}
		}
		return new Look(kind, t.stone(), land, post, beam, walk, stake, parts);
	}

	// ---- the wall ---------------------------------------------------------------------------------------------------------

	/** Raises the next arc of the tier's wall (false: not now, it's waiting on loaded ground or a player to leave). */
	private static boolean arc(ServerLevel level, Faction f, Kind kind, Town t, Look look, boolean all) {
		int r = kind.wall[t.tier() - 1];
		int arc = Hordes.arcNumber(t.arcs());
		CampBuildings.Placer frame = new CampBuildings.Placer(f.seat(), gateOf(t, f), 0);
		double mid = Math.toRadians(arc * 360.0 / ARCS);
		BlockPos middle = frame.at((int)Math.round(Math.sin(mid) * r), 0, (int)Math.round(-Math.cos(mid) * r));
		// (the whole arc loaded, or it waits: no gaps left for want of a chunk)
		for (double edge : new double[] {-0.5, 0.5}) {
			double a = Math.toRadians((arc + edge) * 360.0 / ARCS);
			if (!level.isLoaded(frame.at((int)Math.round(Math.sin(a) * (r + 1)), 0, (int)Math.round(-Math.cos(a) * (r + 1))))) {
				return false;
			}
		}
		if (!level.isLoaded(middle) || !all && Raids.playerNear(level, middle, BUILD_HIDE_RANGE)) {
			return false;
		}
		int floor = f.seat().getY();
		double every = Math.toDegrees(POST_SPACING / (double)r);
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = -r - 1; z <= r + 1; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d < r - 1.5 || d >= r + 0.5 || Hordes.arcOf(x, z) != arc) {
					continue;
				}
				BlockPos column = frame.at(x, 0, z);
				if (!Hordes.ours(level, f, column)) {
					continue;
				}
				int base = Hordes.ground(level, column);
				if (Math.abs(base - floor) > Hordes.MAX_DROP || !level.getFluidState(column.atY(base - 1)).isEmpty()) {
					continue;
				}
				boolean inGate = Hordes.gate(x, z, GATE_HALF);
				if (d < r - 0.5) {
					// the walkway, wherever nothing stands
					BlockPos at = column.atY(base + WALK_HIGH);
					if (!inGate && level.getBlockState(at).isAir()) {
						level.setBlock(at, look.walk(), Hordes.QUIET);
					}
					continue;
				}
				wallColumn(level, frame, column, x, z, base, look, inGate, every);
			}
		}
		// ladders up beside the side and back gates, and the front gate's towers
		for (int[] l : Hordes.ladders(r, GATE_HALF)) {
			if (Hordes.arcOf(l[0], l[1]) == arc) {
				Hordes.ladder(level, f, frame, l[0], l[1], Direction.values()[l[2]], WALK_HIGH);
			}
		}
		if (arc == 0) {
			tower(level, f, frame, -GATE_HALF - 2, -r + 1, look);
			tower(level, f, frame, GATE_HALF + 2, -r + 1, look);
		}
		return true;
	}

	/**
	 * One column of the wall on the land at {@code base}: posts at every {@link #POST_SPACING} blocks round, the wall
	 * between in the look's style, its top (stakes, battlements, or dark tile) and, in a gate, a lintel with the way clear
	 * under it. Anything a player placed or anything built in the way, and it's left as a gap.
	 */
	private static void wallColumn(ServerLevel level, CampBuildings.Placer frame, BlockPos column, int x, int z, int base, Look look, boolean inGate,
		double every) {
		for (int y = 0; y <= POST_HIGH; y++) {
			if (!Hordes.clearable(level, column.atY(base + y))) {
				return;
			}
		}
		if (inGate) {
			for (int y = 0; y < WALL_HIGH - 1; y++) {
				level.setBlock(column.atY(base + y), Blocks.AIR.defaultBlockState(), Hordes.QUIET);
			}
			Direction.Axis along = Math.abs(x) > Math.abs(z) ? Direction.Axis.Z : Direction.Axis.X;
			level.setBlock(column.atY(base + WALL_HIGH - 1), frame.turn(look.beam().setValue(RotatedPillarBlock.AXIS, along)), Hordes.QUIET);
			level.setBlock(column.atY(base + WALL_HIGH), look.gateTop(), Hordes.QUIET);
			return;
		}
		double angle = Math.toDegrees(Math.atan2(z, x)) + 360.0;
		boolean post = angle % every < Math.toDegrees(1.0 / Math.max(1.0, Math.sqrt(x * x + z * z)));
		int high = post ? POST_HIGH : WALL_HIGH;
		for (int y = 0; y < high; y++) {
			level.setBlock(column.atY(base + y), post ? look.post() : look.fill(x, y, z), Hordes.QUIET);
		}
		if (post) {
			level.setBlock(column.atY(base + POST_HIGH), look.stake(), Hordes.QUIET);
		} else if (look.kind() == Kind.BLOSSOM) {
			// tile-capped
			level.setBlock(column.atY(base + WALL_HIGH), tile(), Hordes.QUIET);
		} else if (look.stone()) {
			// battlements: a wall on every other column
			if ((x + z & 1) == 0) {
				level.setBlock(column.atY(base + WALL_HIGH), look.stake(), Hordes.QUIET);
			}
		} else {
			int stakes = (x + z & 1) == 0 ? 2 : 1;
			for (int y = 0; y < stakes; y++) {
				level.setBlock(column.atY(base + WALL_HIGH + y), look.stake(), Hordes.QUIET);
			}
		}
		// (land falling away under the wall: its foot is filled in)
		for (int y = 1; y <= 3; y++) {
			BlockPos under = column.atY(base - y);
			if (!level.getBlockState(under).isAir() || Placed.any(level, under)) {
				break;
			}
			level.setBlock(under, Blocks.COBBLESTONE.defaultBlockState(), Hordes.QUIET);
		}
	}

	/** A gate tower (3 x 3, {@link #TOWER_HIGH} high) at town-frame {@code (x, z)}, on the land there. */
	private static void tower(ServerLevel level, Faction f, CampBuildings.Placer frame, int x, int z, Look look) {
		BlockPos centre = frame.at(x, 0, z);
		if (!Hordes.ours(level, f, centre)) {
			return;
		}
		int base = Hordes.ground(level, centre, true);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos column = frame.at(x + dx, 0, z + dz);
				for (int y = 0; y <= TOWER_HIGH + 2; y++) {
					BlockPos at = column.atY(base + y);
					// (its own wall may already stand there: that's taken into the tower)
					if (!Hordes.clearable(level, at) && !(look.part(level.getBlockState(at)) && !Placed.any(level, at))) {
						return;
					}
				}
			}
		}
		int lift = base - frame.anchor().getY();
		boolean blossom = look.kind() == Kind.BLOSSOM;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 0; y < TOWER_HIGH; y++) {
					boolean corner = dx != 0 && dz != 0;
					level.setBlock(frame.at(x + dx, lift + y, z + dz), corner ? look.post() : look.fill(x + dx, y, z + dz), Hordes.QUIET);
				}
				level.setBlock(frame.at(x + dx, lift + TOWER_HIGH, z + dz), blossom ? Blocks.DEEPSLATE_TILES.defaultBlockState() : look.land().planks(), Hordes.QUIET);
				if (dx != 0 && dz != 0 && !blossom) {
					level.setBlock(frame.at(x + dx, lift + TOWER_HIGH + 1, z + dz), look.stake(), Hordes.QUIET);
				}
			}
		}
		// a lantern on top, and a slit in the town side
		level.setBlock(frame.at(x, lift + TOWER_HIGH + 1, z), Blocks.LANTERN.defaultBlockState(), Hordes.QUIET);
		level.setBlock(frame.at(x, lift + 5, z + 1), Blocks.AIR.defaultBlockState(), Hordes.QUIET);
	}

	/**
	 * The tier's wall is closed: gates are cut in the old ring (right, back and left: the front had one), with ladders
	 * beside them, and roads run out from the old gates to the new ring's. The first wall has no old ring: its roads run
	 * out from the settlement's heart.
	 */
	private static boolean open(ServerLevel level, Faction f, Kind kind, Town t, Look look) {
		int old = t.tier() >= 3 ? kind.wall[t.tier() - 2] : 0, now = kind.wall[t.tier() - 1];
		CampBuildings.Placer frame = new CampBuildings.Placer(f.seat(), gateOf(t, f), 0);
		// (the four ways out loaded from the heart to past the new wall, or it waits)
		for (int[] end : new int[][] {{0, -now - 2}, {now + 2, 0}, {0, now + 2}, {-now - 2, 0}}) {
			if (!level.isLoaded(frame.at(end[0], 0, end[1]))) {
				return false;
			}
		}
		int floor = f.seat().getY();
		for (int x = -old - 1; old > 0 && x <= old + 1; x++) {
			for (int z = -old - 1; z <= old + 1; z++) {
				double d = Math.sqrt(x * x + z * z);
				// (the front gate is there already)
				if (d < old - 1.5 || d >= old + 0.5 || !Hordes.gate(x, z, GATE_HALF) || z < 0 && Math.abs(x) <= GATE_HALF) {
					continue;
				}
				BlockPos column = frame.at(x, 0, z);
				if (!Hordes.ours(level, f, column)) {
					continue;
				}
				// (0.7.490) the column's own wall: its lowest part from the land's surface up (never in the ground under
				// it), up to under a lintel; a gate already open there (a lintel over clear ground) is left as it stands
				boolean walkway = d < old - 0.5;
				int land = surface(level, column, look);
				if (Math.abs(land - floor) > Hordes.MAX_DROP) {
					continue;
				}
				int bottom = Integer.MIN_VALUE;
				for (int y = land; y <= land + (walkway ? WALK_HIGH : WALL_HIGH - 2); y++) {
					BlockPos at = column.atY(y);
					if (look.part(level.getBlockState(at)) && !Placed.any(level, at)) {
						bottom = y;
						break;
					}
				}
				if (bottom == Integer.MIN_VALUE) {
					continue;
				}
				for (int y = bottom; y <= bottom + (walkway ? WALK_HIGH : WALL_HIGH - 2); y++) {
					BlockPos at = column.atY(y);
					if (look.part(level.getBlockState(at)) && !Placed.any(level, at)) {
						level.setBlock(at, Blocks.AIR.defaultBlockState(), Hordes.QUIET);
					}
				}
				BlockPos lintel = column.atY(bottom + WALL_HIGH - 1);
				if (!walkway && look.part(level.getBlockState(lintel)) && !Placed.any(level, lintel)) {
					Direction.Axis along = Math.abs(x) > Math.abs(z) ? Direction.Axis.Z : Direction.Axis.X;
					level.setBlock(lintel, frame.turn(look.beam().setValue(RotatedPillarBlock.AXIS, along)), Hordes.QUIET);
				}
			}
		}
		if (old > 0) {
			for (int[] l : Hordes.ladders(old, GATE_HALF)) {
				Hordes.ladder(level, f, frame, l[0], l[1], Direction.values()[l[2]], WALK_HIGH);
			}
		}
		BlockState road = kind == Kind.BLOSSOM ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT_PATH.defaultBlockState();
		for (int way = 0; way < 4; way++) {
			for (int along = Math.max(old - 2, 4); along <= now + 2; along++) {
				for (int across = -ROAD_HALF; across <= ROAD_HALF; across++) {
					int x = switch (way) {
						case 0 -> across;
						case 1 -> along;
						case 2 -> -across;
						default -> -along;
					};
					int z = switch (way) {
						case 0 -> -along;
						case 1 -> across;
						case 2 -> along;
						default -> -across;
					};
					BlockPos column = frame.at(x, 0, z);
					if (!Hordes.ours(level, f, column)) {
						continue;
					}
					BlockPos surface = column.atY(Hordes.ground(level, column) - 1);
					BlockState s = level.getBlockState(surface);
					if ((s.is(BlockTags.DIRT) || s.is(Blocks.GRASS_BLOCK)) && !s.is(Blocks.COARSE_DIRT) && !Placed.any(level, surface)
						&& level.getBlockState(surface.above()).isAir()) {
						level.setBlock(surface, road, Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		return true;
	}

	/**
	 * (0.7.490) The land's surface under a wall's column (the first air over the ground), down through the wall's own
	 * parts, trees and plants from the top. The wall holds none of the land's own blocks ({@link #earthy}), so this stops
	 * on the ground, never under it.
	 */
	private static int surface(ServerLevel level, BlockPos column, Look look) {
		int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).getY();
		int min = level.getMinY();
		while (y > min + 1) {
			BlockPos below = column.atY(y - 1);
			BlockState s = level.getBlockState(below);
			boolean mine = !Placed.any(level, below);
			if (s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty() || mine && (look.part(s) || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES))) {
				y--;
			} else {
				break;
			}
		}
		return y;
	}

	// ---- the band: what each kind builds -----------------------------------------------------------------------------------------

	// (0.7.490) the tier is in each piece's id and seed: the same key at the next tier is a new building, not the old one's twin
	private static void piece(List<Hordes.Piece> out, ServerLevel level, Faction f, int tier, String key, int n, Function<Buildings.Kit, Blueprint> design) {
		for (int i = 0; i < n; i++) {
			String id = "town_" + f.id() + "_t" + tier + "_" + key + "_" + i;
			long seed = (f.id() * 0x9E3779B97F4A7C15L ^ key.hashCode() * 0x632BE59BD9B4E019L ^ i * 0x2545F4914F6CDD1DL ^ tier * 0x7F4A7C159E3779B9L) | 1L;
			int fid = f.id();
			out.add(new Hordes.Piece(id, () -> {
				Blueprint known = DESIGNS.get(id);
				if (known == null) {
					Faction now = Factions.get(level.getServer(), fid);
					known = design.apply(Buildings.Kit.of(level, now != null ? now : f, seed, seed ^ 0x5DEECE66DL));
					DESIGNS.put(id, known);
				}
				return known;
			}));
		}
	}

	private static void fixed(List<Hordes.Piece> out, Faction f, int tier, String key, int n, Supplier<Blueprint> design) {
		for (int i = 0; i < n; i++) {
			out.add(new Hordes.Piece("town_" + f.id() + "_t" + tier + "_" + key + "_" + i, design));
		}
	}

	/** What goes up in tier {@code tier}'s band, before the planner shuffles and places it. */
	private static List<Hordes.Piece> list(ServerLevel level, Faction f, Kind kind, int tier) {
		List<Hordes.Piece> out = new ArrayList<>();
		if (kind == Kind.VILLAGE) {
			switch (tier) {
				case 2 -> {
					piece(out, level, f, tier, "house", 3, Buildings::house);
					piece(out, level, f, tier, "well", 1, Buildings::well);
					piece(out, level, f, tier, "stall", 2, Buildings::stall);
					piece(out, level, f, tier, "farm", 1, Buildings::farm);
					piece(out, level, f, tier, "pen", 1, Buildings::pen);
				}
				case 3 -> {
					piece(out, level, f, tier, "house", 4, Buildings::house);
					piece(out, level, f, tier, "well", 1, Buildings::well);
					piece(out, level, f, tier, "smithy", 1, Buildings::smithy);
					piece(out, level, f, tier, "mason", 1, Buildings::mason);
					piece(out, level, f, tier, "tavern", 1, k -> Buildings.tavern(k, 8));
					piece(out, level, f, tier, "market", 1, Buildings::market);
					piece(out, level, f, tier, "stall", 2, Buildings::stall);
					piece(out, level, f, tier, "farm", 2, Buildings::farm);
					piece(out, level, f, tier, "cookhouse", 1, Buildings::cookhouse);
				}
				default -> {
					piece(out, level, f, tier, "house", 5, Buildings::house);
					piece(out, level, f, tier, "barracks", 1, Buildings::barracks);
					piece(out, level, f, tier, "market", 1, Buildings::market);
					piece(out, level, f, tier, "tavern", 1, k -> Buildings.tavern(k, 14));
					piece(out, level, f, tier, "smithy", 1, Buildings::smithy);
					piece(out, level, f, tier, "mason", 1, Buildings::mason);
					piece(out, level, f, tier, "cookhouse", 1, Buildings::cookhouse);
					piece(out, level, f, tier, "farm", 2, Buildings::farm);
					piece(out, level, f, tier, "pen", 1, Buildings::pen);
					piece(out, level, f, tier, "well", 1, Buildings::well);
					piece(out, level, f, tier, "stables", 1, Buildings::stableYard);
					piece(out, level, f, tier, "tower", 1, Buildings::tower);
				}
			}
		} else {
			switch (tier) {
				case 2 -> {
					piece(out, level, f, tier, "minka", 3, Blossom::minka);
					piece(out, level, f, tier, "stall", 2, Buildings::stall);
					piece(out, level, f, tier, "well", 1, Buildings::well);
					piece(out, level, f, tier, "smokehouse", 1, JobHalls::smokehouse);
					fixed(out, f, tier, "lantern", 3, Blossom::stoneLantern);
				}
				case 3 -> {
					piece(out, level, f, tier, "minka", 4, Blossom::minka);
					piece(out, level, f, tier, "stall", 2, Buildings::stall);
					piece(out, level, f, tier, "well", 1, Buildings::well);
					piece(out, level, f, tier, "butcher_shop", 1, JobHalls::butcherShop);
					piece(out, level, f, tier, "fishery", 1, JobHalls::fishery);
					piece(out, level, f, tier, "lodge", 1, JobHalls::huntingLodge);
					piece(out, level, f, tier, "tavern", 1, k -> Buildings.tavern(k, 8));
					piece(out, level, f, tier, "farm", 1, Buildings::farm);
					fixed(out, f, tier, "lantern", 4, Blossom::stoneLantern);
				}
				default -> {
					piece(out, level, f, tier, "minka", 5, Blossom::minka);
					piece(out, level, f, tier, "stall", 3, Buildings::stall);
					piece(out, level, f, tier, "butcher", 1, JobHalls::butcher);
					piece(out, level, f, tier, "lodge", 1, JobHalls::huntingLodge);
					piece(out, level, f, tier, "dojo", 1, JobHalls::trainingHall);
					piece(out, level, f, tier, "infirmary", 1, JobHalls::infirmary);
					piece(out, level, f, tier, "tavern", 1, k -> Buildings.tavern(k, 14));
					piece(out, level, f, tier, "market", 1, Buildings::market);
					piece(out, level, f, tier, "farm", 2, Buildings::farm);
					piece(out, level, f, tier, "well", 1, Buildings::well);
					fixed(out, f, tier, "shrine", 1, Blossom::shrine);
					fixed(out, f, tier, "lantern", 6, Blossom::stoneLantern);
				}
			}
		}
		return out;
	}

	/** Village {@code f}'s band plan for {@code tier} (worked out once, and the same every time for the same village). */
	static List<IllagerBuildings.Spot> plan(ServerLevel level, Faction f, Kind kind, int tier) {
		Plan cached = PLANS.get(f.id());
		if (cached != null && cached.tier() == tier) {
			return cached.spots();
		}
		List<IllagerBuildings.Spot> spots = tier < 2 ? List.of() : Hordes.layout(kind.wall[tier - 2] + BAND_INNER, kind.wall[tier - 1] - BAND_OUTER,
			list(level, f, kind, tier), f.id() * 0x9E3779B97F4A7C15L ^ f.seat().asLong() ^ tier * 0x632BE59BD9B4E019L);
		PLANS.put(f.id(), new Plan(tier, spots));
		return spots;
	}

	/**
	 * (0.7.490) Village {@code f}'s band plan for {@code tier} if it's already worked out or the town is loaded (so its
	 * designs read its own land); null otherwise.
	 */
	private static @Nullable List<IllagerBuildings.Spot> planned(ServerLevel level, Faction f, Kind kind, int tier) {
		Plan cached = PLANS.get(f.id());
		if (cached != null && cached.tier() == tier) {
			return cached.spots();
		}
		return level.isPositionEntityTicking(f.seat()) ? plan(level, f, kind, tier) : null;
	}

	private enum Raise {
		/** It stands. */
		PLACED,
		/** It can't stand there (the land's too rough, or something's in the way): passed over. */
		SKIPPED,
		/** Not now (a player near, or the ground not loaded). */
		WAIT
	}

	/**
	 * Raises a band building at once, on its own patch of levelled ground (the median height of its footprint): never over
	 * anything a player placed or anything built, nor on land too rough or far above or below the settlement.
	 */
	private static Raise raise(ServerLevel level, Faction f, Town t, IllagerBuildings.Spot spot, boolean all) {
		CampBuildings.Placer flat = spot.placer(f.seat(), gateOf(t, f));
		Blueprint design = spot.design().get();
		if (!all && Raids.playerNear(level, flat.anchor(), BUILD_HIDE_RANGE)) {
			return Raise.WAIT;
		}
		int[] b = Hordes.bounds(new Hordes.Piece(spot.id(), spot.design()));
		// (0.7.490) never over the builders' own works: a building begun, finished or a plot marked there, or one raised before
		BlockPos ca = flat.at(b[0], 0, b[1]), cc = flat.at(b[2], 0, b[3]);
		int minX = Math.min(ca.getX(), cc.getX()), minZ = Math.min(ca.getZ(), cc.getZ()), maxX = Math.max(ca.getX(), cc.getX()),
			maxZ = Math.max(ca.getZ(), cc.getZ());
		for (int[] w : Planner.taken(level, f)) {
			if (minX <= w[2] && maxX >= w[0] && minZ <= w[3] && maxZ >= w[1]) {
				return Raise.SKIPPED;
			}
		}
		List<BlockPos> columns = new ArrayList<>();
		List<Integer> heights = new ArrayList<>();
		for (int x = b[0]; x <= b[2]; x++) {
			for (int z = b[1]; z <= b[3]; z++) {
				BlockPos column = flat.at(x, 0, z);
				if (!level.isLoaded(column)) {
					return Raise.WAIT;
				}
				if (!Hordes.ours(level, f, column)) {
					return Raise.SKIPPED;
				}
				int g = Hordes.ground(level, column);
				if (!level.getFluidState(column.atY(g - 1)).isEmpty()) {
					return Raise.SKIPPED;
				}
				columns.add(column);
				heights.add(g);
			}
		}
		List<Integer> sorted = new ArrayList<>(heights);
		sorted.sort(null);
		int floor = sorted.get(sorted.size() / 2);
		if (sorted.getLast() - sorted.getFirst() > Hordes.PLOT_SPREAD || Math.abs(floor - f.seat().getY()) > Hordes.MAX_DROP) {
			return Raise.SKIPPED;
		}
		for (int i = 0; i < columns.size(); i++) {
			for (int y = Math.min(heights.get(i), floor); y <= floor + b[4] + 1; y++) {
				if (!Hordes.clearable(level, columns.get(i).atY(y))) {
					return Raise.SKIPPED;
				}
			}
		}
		// the ground levelled: raised with dirt, cut down, the air above cleared
		for (int i = 0; i < columns.size(); i++) {
			BlockPos column = columns.get(i);
			int g = heights.get(i);
			BlockState surface = level.getBlockState(column.atY(g - 1));
			if (!Storehouse.ground(surface)) {
				surface = Blocks.COARSE_DIRT.defaultBlockState();
			}
			for (int y = g; y < floor - 1; y++) {
				level.setBlock(column.atY(y), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			for (int y = floor; y <= floor + b[4] + 1; y++) {
				if (!level.getBlockState(column.atY(y)).isAir()) {
					level.setBlock(column.atY(y), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			if (g != floor) {
				level.setBlock(column.atY(floor - 1), surface, Block.UPDATE_CLIENTS);
			}
		}
		// (a settlement's designs lay their floor in place of the ground: y = 0 is the ground's top)
		CampBuildings.Placer placer = new CampBuildings.Placer(flat.anchor().atY(floor - 1), flat.facing(), flat.width());
		CampBuildings.place(level, design, placer);
		for (Blueprint.Placement p : design.blocks()) {
			Hordes.joinFence(level, placer.at(p.offset()));
		}
		raised(level.getServer(), f.id(), minX, minZ, maxX, maxZ);
		JaymosKingdoms.LOGGER.debug("{} raised a {} in its band", f.name(), spot.id());
		return Raise.PLACED;
	}

	// ---- the command (operators; debug: plain text) -------------------------------------------------------------------------------

	/** {@code /kingdom town [tier [1-4] [now]]}: the nearest village's tier and progress; or move it to a tier. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("town").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(WalledTowns::show)
			.then(Commands.literal("tier").executes(WalledTowns::show)
				.then(Commands.argument("tier", IntegerArgumentType.integer(1, 4))
					.executes(c -> set(c, IntegerArgumentType.getInteger(c, "tier"), false))
					.then(Commands.literal("now").executes(c -> set(c, IntegerArgumentType.getInteger(c, "tier"), true)))));
	}

	private static @Nullable Faction nearest(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Faction best = null;
		double bestDist = Double.MAX_VALUE;
		for (Faction f : Factions.all(c.getSource().getServer(), FactionType.VILLAGE)) {
			double d = Raids.horizontal(f.seat(), player.blockPosition());
			if (grows(c.getSource().getServer(), f) && d < bestDist) {
				best = f;
				bestDist = d;
			}
		}
		if (best == null) {
			c.getSource().sendFailure(Component.literal("No standing village"));
		}
		return best;
	}

	private static int show(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Faction f = nearest(c);
		if (f == null) {
			return 0;
		}
		ServerLevel level = Factions.level(c.getSource().getServer());
		c.getSource().sendSuccess(() -> describe(level, f), false);
		return town(c.getSource().getServer(), f.id()).tier();
	}

	/** The village's tier and its progress (debug text). */
	static Component describe(ServerLevel level, Faction f) {
		MinecraftServer server = level.getServer();
		Town t = town(server, f.id());
		Kind kind = kind(level, f);
		StringBuilder out = new StringBuilder();
		out.append(f.name()).append(": ").append(Component.translatable("camp.jaymoskingdoms.tier." + kind.id + "." + t.tier()).getString())
			.append(" (tier ").append(t.tier()).append(", ").append(t.tier() < 2 ? "no wall" : "wall radius " + kind.wall[t.tier() - 1])
			.append(", folk ").append(Math.max(t.folk(), f.holdings().population())).append('/').append(kind.folk[t.tier() - 1]).append(", purse ")
			.append(t.purse()).append(", ").append(wealth(server, f).key()).append(" realm)");
		if (t.tier() >= 2) {
			List<IllagerBuildings.Spot> band = planned(level, f, kind, t.tier());
			String planned = band == null ? "?" : Integer.toString(band.size());
			out.append("\nthis tier: ").append(t.stone() ? "stone" : kind == Kind.BLOSSOM ? "plaster and tile" : "log").append(" wall ").append(t.arcs())
				.append('/').append(ARCS).append(" arcs").append(t.opened() ? ", gates and roads cut" : "").append(", band ")
				.append(band == null ? t.built() : Math.min(t.built(), band.size())).append('/').append(planned).append(" buildings");
		}
		List<String> missing = missing(level, f, t, kind);
		out.append("\nnext tier: ").append(t.tier() >= kind.tiers() ? "none, the top" : missing.isEmpty() ? "ready (it moves up at its next day)"
			: "needs " + String.join(", ", missing));
		return Component.literal(out.toString());
	}

	/**
	 * Moves the nearest village to {@code target}: up step by step (each tier between built at once; the last goes up over
	 * the coming days, or at once with {@code now}), or down (its walls stay).
	 */
	private static int set(CommandContext<CommandSourceStack> c, int target, boolean now) throws CommandSyntaxException {
		Faction f = nearest(c);
		if (f == null) {
			return 0;
		}
		MinecraftServer server = c.getSource().getServer();
		ServerLevel level = Factions.level(server);
		int id = f.id();
		Kind kind = kind(level, f);
		int goal = Math.min(target, kind.tiers());
		Town t = town(server, id);
		if (goal < t.tier()) {
			put(server, id, new Town(goal, t.since(), ARCS, Integer.MAX_VALUE / 2, true, t.stone(), t.purse(), t.born(), t.lastDay(), t.folk(), t.gate()));
			PLANS.remove(id);
		} else {
			complete(level, id);
			while (town(server, id).tier() < goal) {
				Faction fresh = Factions.get(server, id);
				if (fresh == null) {
					return 0;
				}
				promote(level, fresh, town(server, id), true);
				if (town(server, id).tier() < goal || now) {
					complete(level, id);
				}
			}
		}
		Faction after = Factions.get(server, id);
		if (after != null) {
			c.getSource().sendSuccess(() -> describe(level, after), true);
		}
		return 1;
	}
}
