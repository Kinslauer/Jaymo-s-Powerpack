package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.Holder;

/**
 * Illager camps: a second kind of camp ({@link FactionType#BARBARIAN} factions whose {@link Camps.CampData#kind} is
 * {@link #KIND}), riding on the barbarian camps' machinery: they rise in the wild near kingdoms (in pillager country:
 * dark forests, taigas, plains, savannas, meadows and snowy lands), grow daily, send war parties of pillagers and
 * vindicators against the nearest settlement when a player is near it ({@link Raids}), keep a loot pile, and are cleared
 * by killing their warlord ({@link Camps#chiefSlain}). Their people are real illagers ({@link Illagers}).
 *
 * <p>Illager camps stand with each other and are at war with the barbarian camps (and everyone else). A camp grows by
 * its own hands rather than with builders: each day it takes in wood, wool and supplies, takes on another illager now
 * and then (evokers and a ravager as it ages), and raises the next of {@link IllagerBuildings#GROWTH} from its loot pile.
 */
final class IllagerCamps {
	/** {@link Camps.CampData#kind} of an illager camp. */
	static final String KIND = "illager";
	/** At most this many standing illager camps near a kingdom (within {@link Camps#CAMP_AREA}); the chance one rises, each spawn check. */
	static final int CAMPS_PER_KINGDOM = 1;
	static final float CAMP_CHANCE = 0.15F;
	/** The most the ground may rise and fall across a camp. */
	static final int MAX_SPREAD = 5;
	/** A new camp's people, and how its band grows: one more every so many days, up to a most. */
	static final int START_PILLAGERS = 3, START_VINDICATORS = 2, DAYS_PER_ILLAGER = 2, MAX_FIGHTERS = 12;
	/** Pillagers to each vindicator as the band grows. */
	static final int PILLAGERS_PER_VINDICATOR = 2;
	/** Evokers join when the camp is this many days old (one each); a ravager at this age. */
	static final int[] EVOKER_DAYS = {3, 7};
	static final int RAVAGER_DAYS = 5;
	/** What a camp takes in each day by its own hands (felling, shearing, fletching, foraging). */
	static final int DAY_LOGS = 8, DAY_PLANKS = 8, DAY_WOOL = 6, DAY_GREY_WOOL = 4, DAY_FENCES = 4, DAY_COBBLE = 8, DAY_ARROWS = 8,
		DAY_BREAD = 4, DAY_IRON = 1;
	/** The loot pile a new camp starts with. */
	static final int START_LOGS = 32, START_ARROWS = 32, START_BREAD = 16, START_EMERALDS = 5;
	/** Raising a building: one for each day it has grown at most, from this much of its loot pile; never with a player this near. */
	static final int BUILDS_PER_DAY = 1, BUILD_LOGS = 6, BUILD_PLANKS = 8, BUILD_WOOL = 6;
	static final double BUILD_HIDE_RANGE = 24.0;
	/** A warlord missing (his camp loaded, him nowhere) this long (ticks) is replaced: he was taken by something that isn't death. */
	static final long RESEAT_TICKS = 20 * 120;

	private static final String[] NAME_FIRST = {"Grim", "Raven", "Ash", "Gallow", "Thorn", "Black", "Vex", "Dusk", "Mire", "Gloom", "Hollow",
		"Wither", "Crow", "Sorrow", "Iron", "Rook"};
	private static final String[] NAME_SECOND = {"hold", "watch", "moor", "fall", "gate", "fen", "crag", "reach", "wood", "spire", "mark",
		"hollow"};
	private static final DyeColor[] COLOURS = {DyeColor.GRAY, DyeColor.BLACK, DyeColor.LIGHT_GRAY, DyeColor.WHITE};

	/** Camp id → ticks its warlord has been missing. */
	private static final Map<Integer, Long> MISSING = new HashMap<>();

	private IllagerCamps() {
	}

	static boolean is(MinecraftServer server, int camp) {
		Camps.CampData data = Camps.data(server, camp);
		return data != null && KIND.equals(data.kind());
	}

	/** Pillager country: where illager camps may rise. */
	static boolean land(ServerLevel level, BlockPos at) {
		Holder<Biome> biome = level.getBiome(at);
		return biome.is(BiomeTags.HAS_PILLAGER_OUTPOST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.PALE_GARDEN);
	}

	// ---- founding ------------------------------------------------------------------------------------------------------

	/** A new illager camp at {@code center} (its fire on {@code floor}), its gate facing {@code gate}: built, its warlord seated, manned. */
	static Faction found(ServerLevel level, BlockPos center, int floor, Direction gate) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		BlockPos fire = new BlockPos(center.getX(), floor, center.getZ());
		Faction camp = Faction.create(Factions.nextId(server), FactionType.BARBARIAN, name(server, random), COLOURS[random.nextInt(COLOURS.length)], fire,
			KingdomEvents.claim(server, fire, Camps.CLAIM_RADIUS));
		Factions.put(server, camp);
		int id = camp.id();
		long now = level.getGameTime();
		// (its data first: everything after asks what kind of camp it is)
		Camps.putData(server, id, new Camps.CampData(fire, gate, now, now, Production.day(server), -1L, List.of(), 0L, Map.of(), KIND, 0, 0));
		IllagerBuildings.Layout layout = IllagerBuildings.build(level, camp, center, floor, gate);
		Storage.register(server, id, layout.storage(), layout.depot());
		Faction stocked = Factions.get(server, id);
		Storage.put(level, stocked, Items.DARK_OAK_LOG, START_LOGS);
		Storage.put(level, stocked, Items.ARROW, START_ARROWS);
		Storage.put(level, stocked, Items.BREAD, START_BREAD);
		Storage.put(level, stocked, Items.EMERALD, START_EMERALDS);
		Camps.takeSides(server, id, KIND);
		Illagers.crown(level, Factions.get(server, id), layout.warlordSpot());
		// a sentry on the watchtower, the rest about the fire
		Illagers.spawn(level, Factions.get(server, id), Illagers.PILLAGER, layout.sentry(), layout.sentry(), 2, false);
		for (int i = 1; i < START_PILLAGERS; i++) {
			Illagers.spawn(level, Factions.get(server, id), Illagers.PILLAGER, spot(level, fire, random), fire, IllagerBuildings.HOME_RADIUS, false);
		}
		for (int i = 0; i < START_VINDICATORS; i++) {
			Illagers.spawn(level, Factions.get(server, id), Illagers.VINDICATOR, spot(level, fire, random), fire, IllagerBuildings.HOME_RADIUS, false);
		}
		// and the captive in the cage
		Mob allay = EntityTypes.ALLAY.create(level, EntitySpawnReason.EVENT);
		if (allay != null) {
			BlockPos cage = layout.cage();
			allay.snapTo(cage.getX() + 0.5, cage.getY(), cage.getZ() + 0.5, 0.0F, 0.0F);
			allay.setPersistenceRequired();
			level.addFreshEntity(allay);
		}
		JaymosKingdoms.LOGGER.info("An illager camp, {}, rose at {}", camp.name(), fire);
		return Factions.get(server, id);
	}

	private static String name(MinecraftServer server, RandomSource random) {
		String name = "";
		for (int tries = 0; tries < 20; tries++) {
			name = NAME_FIRST[random.nextInt(NAME_FIRST.length)] + NAME_SECOND[random.nextInt(NAME_SECOND.length)];
			if (Factions.byName(server, name) == null) {
				return name;
			}
		}
		return name + Factions.nextId(server);
	}

	/** Open ground on the camp's floor near its fire (not on a bench or in a tent), for one of its people to stand. */
	static BlockPos spot(ServerLevel level, BlockPos fire, RandomSource random) {
		for (int tries = 0; tries < 16; tries++) {
			BlockPos column = fire.offset(random.nextInt(13) - 6, 0, random.nextInt(13) - 6);
			if (column.distManhattan(fire) < 3 || !level.isLoaded(column)) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (at.getY() == fire.getY() && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
				return at;
			}
		}
		return fire.offset(2, 0, 2);
	}

	// ---- growing ---------------------------------------------------------------------------------------------------------

	/** {@code days}' growth: what the camp takes in by its own hands, and (as it ages) more illagers. */
	static void grow(ServerLevel level, Faction camp, int days) {
		MinecraftServer server = level.getServer();
		Map<Item, Integer> takings = new LinkedHashMap<>();
		takings.put(Items.DARK_OAK_LOG, DAY_LOGS);
		takings.put(Items.DARK_OAK_PLANKS, DAY_PLANKS);
		takings.put(Items.WOOL.pick(DyeColor.WHITE), DAY_WOOL);
		takings.put(Items.WOOL.pick(DyeColor.GRAY), DAY_GREY_WOOL);
		takings.put(Items.DARK_OAK_FENCE, DAY_FENCES);
		takings.put(Items.COBBLESTONE, DAY_COBBLE);
		takings.put(Items.ARROW, DAY_ARROWS);
		takings.put(Items.BREAD, DAY_BREAD);
		takings.put(Items.IRON_INGOT, DAY_IRON);
		if (Storage.loaded(level, camp)) {
			for (var e : takings.entrySet()) {
				Storage.put(level, camp, e.getKey(), e.getValue() * days);
			}
		}
		Camps.updateData(server, camp.id(), d -> d.withGrown(d.grown() + days));
		Camps.CampData data = Camps.data(server, camp.id());
		if (data == null || level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		recount(level, camp, data);
		long age = data.grown();
		int cap = (int)Math.min(MAX_FIGHTERS, START_PILLAGERS + START_VINDICATORS + age / DAYS_PER_ILLAGER);
		RandomSource random = level.getRandom();
		BlockPos fire = data.center();
		for (int i = 0; i < days; i++) {
			Faction now = Factions.get(server, camp.id());
			if (now == null || Illagers.fighters(now) >= cap) {
				break;
			}
			int pillagers = now.holdings().count(Illagers.PILLAGER), vindicators = now.holdings().count(Illagers.VINDICATOR);
			int role = pillagers < vindicators * PILLAGERS_PER_VINDICATOR ? Illagers.PILLAGER : Illagers.VINDICATOR;
			Illagers.spawn(level, now, role, spot(level, fire, random), fire, IllagerBuildings.HOME_RADIUS, false);
		}
		int evokers = 0;
		for (int day : EVOKER_DAYS) {
			if (age >= day) {
				evokers++;
			}
		}
		Faction now = Factions.get(server, camp.id());
		if (now != null && now.holdings().count(Illagers.EVOKER) < evokers) {
			Illagers.spawn(level, now, Illagers.EVOKER, spot(level, fire, random), fire, IllagerBuildings.HOME_RADIUS, false);
		}
		now = Factions.get(server, camp.id());
		if (now != null && age >= RAVAGER_DAYS && now.holdings().count(Illagers.RAVAGER) < 1) {
			Illagers.spawn(level, now, Illagers.RAVAGER, fire.relative(data.gate(), 8), fire, IllagerBuildings.HOME_RADIUS, false);
		}
	}

	/**
	 * The roster set right by who's actually there (when the whole camp is loaded): illagers can vanish without dying
	 * (peaceful difficulty, commands), and the roster mustn't keep counting them.
	 */
	static void recount(ServerLevel level, Faction camp, Camps.CampData data) {
		if (!wholeCampLoaded(level, data.center())) {
			return;
		}
		Map<Integer, Integer> there = Illagers.census(camp.id());
		Factions.update(level.getServer(), camp.id(), f -> {
			Faction.Holdings h = f.holdings();
			for (int role : Illagers.rosterRoles()) {
				int counted = there.getOrDefault(role, 0);
				if (h.count(role) > counted) {
					h = h.adjust(role, counted - h.count(role));
				}
			}
			return f.withHoldings(h);
		});
	}

	static boolean wholeCampLoaded(ServerLevel level, BlockPos fire) {
		int r = IllagerBuildings.RING_RADIUS;
		for (BlockPos corner : new BlockPos[] {fire, fire.offset(-r, 0, -r), fire.offset(r, 0, -r), fire.offset(-r, 0, r), fire.offset(r, 0, r)}) {
			if (!level.isPositionEntityTicking(corner)) {
				return false;
			}
		}
		return true;
	}

	/** Every {@link Camps#CHECK_TICKS} while a standing camp is loaded: its warlord replaced if he's gone, its next building raised. */
	static void tick(ServerLevel level, Faction camp, Camps.CampData data, long now, boolean force) {
		MinecraftServer server = level.getServer();
		// the warlord
		boolean present = camp.leader().isPresent() && level.getEntity(camp.leader().get()) != null;
		if (present || !wholeCampLoaded(level, data.center()) || level.getDifficulty() == Difficulty.PEACEFUL) {
			MISSING.remove(camp.id());
		} else if (MISSING.merge(camp.id(), (long)Camps.CHECK_TICKS, Long::sum) >= RESEAT_TICKS) {
			MISSING.remove(camp.id());
			Illagers.crown(level, camp, IllagerBuildings.warlordSpot(data.center(), data.gate()));
			JaymosKingdoms.LOGGER.info("{} has a new warlord (the last was lost)", camp.name());
		}
		// its next building
		long age = data.grown();
		int allowed = (int)Math.min(IllagerBuildings.GROWTH.size(), age * BUILDS_PER_DAY);
		if (data.built() >= allowed || !Storage.loaded(level, camp)) {
			return;
		}
		IllagerBuildings.Spot spot = IllagerBuildings.GROWTH.get(data.built());
		BlockPos where = spot.placer(data.center(), data.gate()).at(0, 0, 0);
		if (!force && Raids.playerNear(level, where, BUILD_HIDE_RANGE)) {
			return;
		}
		if (!IllagerBuildings.clear(level, spot, data.center(), data.gate())) {
			// (something's in the way: that one is skipped)
			Camps.updateData(server, camp.id(), d -> d.withBuilt(d.built() + 1));
			return;
		}
		Map<Item, Integer> cost = Map.of(Items.DARK_OAK_LOG, BUILD_LOGS, Items.DARK_OAK_PLANKS, BUILD_PLANKS, Items.WOOL.pick(DyeColor.WHITE), BUILD_WOOL);
		if (!Storage.has(level, camp, cost)) {
			return;
		}
		for (var e : cost.entrySet()) {
			Storage.take(level, camp, e.getKey(), e.getValue());
		}
		IllagerBuildings.place(level, spot, data.center(), data.gate());
		Camps.updateData(server, camp.id(), d -> d.withBuilt(d.built() + 1));
		JaymosKingdoms.LOGGER.debug("{} raised a {}", camp.name(), spot.id());
	}

	static void clear() {
		MISSING.clear();
	}
}
