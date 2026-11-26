package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jspecify.annotations.Nullable;

/**
 * Bandit camps by the trade roads (the structure placement plan, track 2: "beside a busy road between two settlements, in
 * cover; keeps away from watchtowers"). Bandits, masked outlaws of the common folk (Lore.md), already hold the towers and
 * carts no realm has reached ({@link IllagerSites#BANDIT}); here a band pitches its own camp where the coin travels:
 * <ul>
 * <li>Beside a road ({@link Roads}) whose two ends both stand, {@link #SIDE_MIN}-{@link #SIDE_MAX} blocks off it, in a
 * clearing with trees round it ({@link #covered}), far from either settlement and every other camp ({@link Camps#open}),
 * out of every player's sight.</li>
 * <li>Not where a watchtower may stand within {@link #TOWER_CHUNKS} chunks (tag {@code towers_on_borders}): a realm
 * that watches its road keeps bandits off it.</li>
 * <li>At most {@link #PER_REALM} near any one settlement (within {@link Camps#CAMP_AREA}), one rising now and then
 * ({@link #CHANCE}) while a player travels the road.</li>
 * <li>A barbarian camp's tents, fire and loot pile ({@link CampBuildings}), a chief and a band in masks, leather and blades
 * ({@link IllagerSites#dress}), who grow by one a few days apart up to {@link #MOST}. They stand with the other bandits;
 * kill the chief to clear it, and the roads are safer for it (the bandit hold's own message).</li>
 * </ul>
 * A camp of kind {@link IllagerSites#BANDIT} that isn't seated in one of Illager Structures' places is one of these.
 */
final class BanditCamps {
	/** Each spawn check of {@link Camps}, the chance a band looks for a road; at most this many near any one settlement. */
	static final float CHANCE = 0.12F;
	static final int PER_REALM = 2;
	/** A camp stands this far off the road (blocks). */
	static final int SIDE_MIN = 14, SIDE_MAX = 26;
	/** A player on the road this near (blocks) is who a band rises near (still out of their sight, {@link Camps#HIDE_RANGE}). */
	static final double TRAVELLER = 192.0;
	/** Trees round the clearing: of {@link #COVER_LOOKS} spots this far out (blocks), at least {@link #COVER_LEAST} under leaves. */
	static final int COVER_RING = 12, COVER_LOOKS = 12, COVER_LEAST = 4;
	/** No watchtower may stand within this many chunks. */
	static final int TOWER_CHUNKS = 12;
	/** Road points tried per check. */
	static final int TRIES = 8;
	/** A new band's size, and the most it grows to: one more every this many days. */
	static final int START = 4, MOST = 9, DAYS_PER_BANDIT = 2;
	/** What a band brings in each day (from the road). */
	static final int DAY_BREAD = 3, DAY_LEATHER = 2, DAY_ARROWS = 4, DAY_EMERALDS = 1;

	static final TagKey<Structure> TOWERS = TagKey.create(Registries.STRUCTURE, JaymosKingdoms.id("towers_on_borders"));

	private BanditCamps() {
	}

	/** {@code camp} is a road bandits' camp. */
	static boolean is(MinecraftServer server, int camp) {
		return IllagerSites.BANDIT.equals(Camps.kind(server, camp)) && !IllagerSites.is(server, camp);
	}

	/** Now and then, a band looks for a road near a traveller to pitch by (from {@link Camps}' spawn check). */
	static boolean trySpawn(MinecraftServer server, ServerLevel level) {
		RandomSource random = level.getRandom();
		if (level.getDifficulty() == Difficulty.PEACEFUL || random.nextFloat() >= CHANCE) {
			return false;
		}
		List<Roads.Road> roads = new ArrayList<>();
		for (Roads.Road road : Roads.network(server).roads()) {
			if (road.points().size() >= 3 && standing(server, road.a()) != null && standing(server, road.b()) != null) {
				roads.add(road);
			}
		}
		for (int tries = 0; tries < TRIES && !roads.isEmpty(); tries++) {
			Roads.Road road = roads.get(random.nextInt(roads.size()));
			Faction a = standing(server, road.a()), b = standing(server, road.b());
			if (a == null || b == null || near(server, a) >= PER_REALM || near(server, b) >= PER_REALM) {
				continue;
			}
			int i = 1 + random.nextInt(road.points().size() - 2);
			BlockPos on = BlockPos.of(road.points().get(i));
			if (!Raids.playerNear(level, on, TRAVELLER)) {
				continue;
			}
			BlockPos at = beside(road, i, random);
			if (!Camps.open(server, level, at) || !covered(level, at) || towerNear(level, at)) {
				continue;
			}
			int floor = CampBuildings.floor(level, at, Camps.MAX_SPREAD);
			if (floor == Integer.MIN_VALUE) {
				continue;
			}
			found(level, at, floor, Direction.getApproximateNearest(on.getX() - at.getX(), 0, on.getZ() - at.getZ()));
			return true;
		}
		return false;
	}

	private static @Nullable Faction standing(MinecraftServer server, int id) {
		Faction f = Factions.get(server, id);
		return f == null || f.fallen() ? null : f;
	}

	/** Road bandits' camps standing within {@link Camps#CAMP_AREA} of {@code f}'s seat. */
	private static long near(MinecraftServer server, Faction f) {
		return Factions.all(server, FactionType.BARBARIAN).stream()
			.filter(c -> !c.fallen() && is(server, c.id()) && Raids.horizontal(c.seat(), f.seat()) <= Camps.CAMP_AREA * Camps.CAMP_AREA).count();
	}

	/** A spot off to one side of road point {@code i}, square to the road's way there. */
	private static BlockPos beside(Roads.Road road, int i, RandomSource random) {
		BlockPos prev = BlockPos.of(road.points().get(i - 1)), next = BlockPos.of(road.points().get(i + 1)), on = BlockPos.of(road.points().get(i));
		double dx = next.getX() - prev.getX(), dz = next.getZ() - prev.getZ();
		double len = Math.max(1.0E-3, Math.hypot(dx, dz));
		int side = (random.nextBoolean() ? 1 : -1) * (SIDE_MIN + random.nextInt(SIDE_MAX - SIDE_MIN + 1));
		return on.offset((int) Math.round(-dz / len * side), 0, (int) Math.round(dx / len * side));
	}

	/** Trees round {@code at}: enough spots on a ring about it are under leaves (the leafy top above the bare one). */
	static boolean covered(ServerLevel level, BlockPos at) {
		int under = 0;
		for (int k = 0; k < COVER_LOOKS; k++) {
			double angle = Math.PI * 2.0 * k / COVER_LOOKS;
			BlockPos spot = at.offset((int) Math.round(Math.cos(angle) * COVER_RING), 0, (int) Math.round(Math.sin(angle) * COVER_RING));
			if (!level.isLoaded(spot)) {
				return false;
			}
			int leafy = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spot.getX(), spot.getZ());
			int bare = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.getX(), spot.getZ());
			if (leafy > bare + 1 && ++under >= COVER_LEAST) {
				return true;
			}
		}
		return false;
	}

	/** Whether a watchtower may stand within {@link #TOWER_CHUNKS} chunks of {@code at} (by its set's spread; nothing made). */
	private static boolean towerNear(ServerLevel level, BlockPos at) {
		var state = level.getChunkSource().getGeneratorState();
		int cx = at.getX() >> 4, cz = at.getZ() >> 4;
		for (Holder<StructureSet> set : state.possibleStructureSets()) {
			boolean towers = false;
			for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
				towers |= entry.structure().is(TOWERS);
			}
			if (towers && state.hasStructureChunkInRange(set, cx, cz, TOWER_CHUNKS)) {
				return true;
			}
		}
		return false;
	}

	/** A new road bandits' camp at {@code center} (its fire on {@code floor}), its gate toward the road: built, its chief seated, manned. */
	static Faction found(ServerLevel level, BlockPos center, int floor, Direction gate) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		BlockPos fire = new BlockPos(center.getX(), floor, center.getZ());
		Faction camp = Faction.create(Factions.nextId(server), FactionType.BARBARIAN, IllagerSites.name(server, "bandits", random),
			DyeColor.values()[random.nextInt(DyeColor.values().length)], fire, KingdomEvents.claim(server, fire, Camps.CLAIM_RADIUS));
		Factions.put(server, camp);
		int id = camp.id();
		long now = level.getGameTime();
		// (its data first: everything after asks what kind of camp it is)
		Camps.putData(server, id, new Camps.CampData(fire, gate, now, now, Production.day(server), -1L, List.of(), 0L, Map.of(),
			IllagerSites.BANDIT, 0, 0));
		CampBuildings.Layout layout = CampBuildings.build(level, Factions.get(server, id), center, floor, gate);
		Storage.register(server, id, layout.storage(), layout.depot());
		Camps.stock(level, Factions.get(server, id));
		Camps.takeSides(server, id, IllagerSites.BANDIT);
		Faction crowned = KingdomEvents.crown(level, Factions.get(server, id));
		if (crowned.leader().isPresent() && level.getEntity(crowned.leader().get()) instanceof Folk chief) {
			BlockPos spot = layout.chiefSpot();
			chief.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, gate.toYRot(), 0.0F);
			chief.setHomeTo(spot, Camps.CHIEF_HOME_RADIUS);
			IllagerSites.dress(chief, id, true);
		}
		for (int i = 0; i < START; i++) {
			hire(level, id);
		}
		JaymosKingdoms.LOGGER.info("A bandit camp, {}, rose by the road at {}", camp.name(), fire);
		return Factions.get(server, id);
	}

	/** One more bandit for camp {@code id}, masked. */
	private static void hire(ServerLevel level, int id) {
		Faction now = Factions.get(level.getServer(), id);
		Folk bandit = now == null ? null : KingdomEvents.spawn(level, now, Folk.BARBARIAN);
		if (bandit != null) {
			IllagerSites.dress(bandit, id, true);
		}
	}

	/** {@code days}' growth (from {@link Camps#grow}): what the road brings in, and another bandit every few days. */
	static void grow(ServerLevel level, Faction camp, int days) {
		MinecraftServer server = level.getServer();
		if (Storage.loaded(level, camp)) {
			Storage.put(level, camp, Items.BREAD, DAY_BREAD * days);
			Storage.put(level, camp, Items.LEATHER, DAY_LEATHER * days);
			Storage.put(level, camp, Items.ARROW, DAY_ARROWS * days);
			Storage.put(level, camp, Items.EMERALD, DAY_EMERALDS * days);
		}
		Camps.CampData data = Camps.data(server, camp.id());
		long age = data == null ? 0 : (level.getGameTime() - data.founded()) / Camps.DAY_TICKS;
		int cap = (int) Math.min(MOST, START + age / DAYS_PER_BANDIT);
		for (int i = 0; i < days; i++) {
			Faction now = Factions.get(server, camp.id());
			if (now == null || now.holdings().count(Folk.BARBARIAN) + now.holdings().vacancies().getOrDefault(Folk.roleName(Folk.BARBARIAN), 0) >= cap
				|| Raids.playerNear(level, now.seat(), Camps.HIDE_RANGE)) {
				break;
			}
			hire(level, camp.id());
		}
	}
}
