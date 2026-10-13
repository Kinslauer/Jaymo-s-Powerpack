package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.45) Orc war camps: a fourth kind of camp ({@link FactionType#BARBARIAN} factions whose {@link Camps.CampData#kind}
 * is {@link #KIND}), riding on the camps' machinery. One may rise in the rough country near a kingdom (savanna, badlands,
 * taiga, dark forest, hills, plains and snow), a dark oak stockade round a war fire, the warchief in his longhouse, grunts
 * sparring in the pit, rangers on the towers, a shaman by the fire ({@link OrcBuildings}, {@link Orcs}). It grows daily by
 * its own hands (more orcs, more tents), and its war bands raid the nearest kingdom ({@link Raids}). Kill its warchief and
 * it is cleared like any camp ({@link Camps#chiefSlain}).
 */
final class OrcCamps {
	/** {@link Camps.CampData#kind} of an orc camp. */
	static final String KIND = "orc";
	/** At most this many standing orc camps near a kingdom (within {@link Camps#CAMP_AREA}); the chance one rises, each spawn check. */
	static final int CAMPS_PER_KINGDOM = 1;
	static final float CAMP_CHANCE = 0.12F;
	/** The most the ground may rise and fall across a camp. */
	static final int MAX_SPREAD = 5;
	/** A new camp's orcs, and how it grows: another orc every so many days, every so many of them a ranger, up to a most. */
	static final int START_GRUNTS = 6, START_SPARRING = 2, START_RANGERS = 3, DAYS_PER_ORC = 2, RANGER_EVERY = 4, MAX_ORCS = 15;
	/** What a camp takes in each day by its own hands (hunting, felling, smithing, tanning). */
	static final int DAY_LOGS = 6, DAY_HIDES = 6, DAY_BONES = 3, DAY_MEAT = 6, DAY_IRON = 6, DAY_ARROWS = 8, DAY_LEATHER = 2;
	/** The plunder a new camp starts with. */
	static final int START_LOGS = 24, START_HIDES = 16, START_MEAT = 16, START_ARROWS = 32, START_IRON = 8, START_EMERALDS = 3;
	/** Raising a building: one a day at most, from this much of its pile; never with a player this near. */
	static final int BUILD_LOGS = 4, BUILD_HIDES = 4;
	static final double BUILD_HIDE_RANGE = 24.0;
	/** The warchief keeps this near his throne; sparring grunts to the pit; a ranger to his tower; the smith to the forge. */
	static final int WARCHIEF_HOME_RADIUS = 3, PIT_RADIUS = 3, RANGER_RADIUS = 1, SMITH_RADIUS = 3;
	/** A warchief missing (his camp loaded, him nowhere) this long (ticks) is replaced. */
	static final long RESEAT_TICKS = 20 * 120;

	private static final String[] NAME_FIRST = {"Gor", "Skull", "Black", "Blood", "Iron", "Ash", "Grim", "Rot", "Gut", "Bone", "Tusk", "Dread",
		"Mog", "Uruk", "Krag"};
	private static final String[] NAME_SECOND = {"hold", "maw", "pit", "fang", "spike", "burrow", "crag", "gash", "horde", "rock", "pyre", "scar"};
	private static final DyeColor[] COLOURS = {DyeColor.RED, DyeColor.BLACK, DyeColor.GRAY};

	/** Camp id → ticks its warchief has been missing. */
	private static final Map<Integer, Long> MISSING = new HashMap<>();

	private OrcCamps() {
	}

	static boolean is(MinecraftServer server, int camp) {
		Camps.CampData data = Camps.data(server, camp);
		return data != null && KIND.equals(data.kind());
	}

	/** Orc country: savanna, badlands, taiga, dark forest, hills, plains and snowy plains (not jungles, swamps, deserts or the sea). */
	static boolean land(ServerLevel level, BlockPos at) {
		Holder<Biome> biome = level.getBiome(at);
		return biome.is(BiomeTags.IS_SAVANNA) || biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_HILL)
			|| biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.PLAINS) || biome.is(Biomes.SNOWY_PLAINS) || biome.is(Biomes.MEADOW);
	}

	// ---- founding ------------------------------------------------------------------------------------------------------

	/** A new orc camp at {@code center} (its fire on {@code floor}), its gate facing {@code gate}: built, its warchief seated, manned. */
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
		OrcBuildings.Layout layout = OrcBuildings.build(level, center, floor, gate);
		Storage.register(server, id, layout.storage(), layout.depot());
		Faction stocked = Factions.get(server, id);
		Storage.put(level, stocked, Items.DARK_OAK_LOG, START_LOGS);
		Storage.put(level, stocked, Items.WOOL.pick(DyeColor.BLACK), START_HIDES);
		Storage.put(level, stocked, Items.COOKED_BEEF, START_MEAT);
		Storage.put(level, stocked, Items.ARROW, START_ARROWS);
		Storage.put(level, stocked, Items.IRON_INGOT, START_IRON);
		Storage.put(level, stocked, Items.EMERALD, START_EMERALDS);
		Camps.takeSides(server, id, KIND);
		crown(level, Factions.get(server, id), layout.throne(), gate);
		// rangers on the watchtower and the gate towers, grunts in the pit and at the forge, the rest about the fire, the shaman by it
		spawn(level, id, Orcs.RANGER, layout.tower(), layout.tower(), RANGER_RADIUS);
		for (int i = 1; i < START_RANGERS; i++) {
			BlockPos top = OrcBuildings.towerTop(fire, gate, i % 2 == 1 ? 1 : -1);
			spawn(level, id, Orcs.RANGER, top, top, RANGER_RADIUS);
		}
		for (int i = 0; i < START_SPARRING; i++) {
			spawn(level, id, Orcs.GRUNT, layout.pit(), layout.pit(), PIT_RADIUS);
		}
		spawn(level, id, Orcs.GRUNT, layout.forge(), layout.forge(), SMITH_RADIUS);
		for (int i = START_SPARRING + 1; i < START_GRUNTS; i++) {
			spawn(level, id, Orcs.GRUNT, spot(level, fire, random), fire, OrcBuildings.HOME_RADIUS);
		}
		spawn(level, id, Orcs.SHAMAN, fire.offset(2, 0, 2), fire, OrcBuildings.HOME_RADIUS / 2);
		JaymosKingdoms.LOGGER.info("An orc camp, {}, rose at {}", camp.name(), fire);
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

	/** Seats a new warchief at his throne and makes him the camp's leader. Returns the camp as stored. */
	static @Nullable Faction crown(ServerLevel level, @Nullable Faction camp, BlockPos throne, Direction gate) {
		if (camp == null) {
			return null;
		}
		Folk chief = make(level, camp, Orcs.WARCHIEF, throne.relative(gate), throne, WARCHIEF_HOME_RADIUS);
		if (chief != null) {
			chief.snapTo(throne.getX() + 0.5, throne.getY() + 0.5, throne.getZ() + 0.5, gate.toYRot(), 0.0F);
		}
		return Factions.update(level.getServer(), camp.id(), f -> f.withLeader(chief == null ? Optional.empty() : Optional.of(chief.getUUID())));
	}

	/** One of the camp's orcs at {@code at}, keeping {@code radius} of {@code home}, counted on its roster. */
	static @Nullable Folk spawn(ServerLevel level, int id, int role, BlockPos at, BlockPos home, int radius) {
		Faction camp = Factions.get(level.getServer(), id);
		if (camp == null) {
			return null;
		}
		Folk orc = make(level, camp, role, at, home, radius);
		if (orc != null) {
			Factions.update(level.getServer(), id, f -> f.withHoldings(f.holdings().adjust(role, 1)));
		}
		return orc;
	}

	private static @Nullable Folk make(ServerLevel level, Faction camp, int role, BlockPos at, BlockPos home, int radius) {
		Folk orc = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.STRUCTURE);
		if (orc == null) {
			return null;
		}
		orc.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		orc.setup(role, camp, home, radius);
		level.addFreshEntity(orc);
		return orc;
	}

	/** Open ground on the camp's floor near its fire (not in a tent), for one of its orcs to stand. */
	static BlockPos spot(ServerLevel level, BlockPos fire, RandomSource random) {
		for (int tries = 0; tries < 16; tries++) {
			BlockPos column = fire.offset(random.nextInt(15) - 7, 0, random.nextInt(15) - 7);
			if (column.distManhattan(fire) < 3 || !level.isLoaded(column)) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (at.getY() == fire.getY() && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
				return at;
			}
		}
		return fire.offset(3, 0, 1);
	}

	// ---- growing ---------------------------------------------------------------------------------------------------------

	/** {@code days}' growth: what the camp takes in by its own hands, and (as it ages) more orcs. */
	static void grow(ServerLevel level, Faction camp, int days) {
		MinecraftServer server = level.getServer();
		Map<Item, Integer> takings = new LinkedHashMap<>();
		takings.put(Items.DARK_OAK_LOG, DAY_LOGS);
		takings.put(Items.WOOL.pick(DyeColor.BLACK), DAY_HIDES / 2);
		takings.put(Items.WOOL.pick(DyeColor.BROWN), DAY_HIDES / 2);
		takings.put(Items.BONE, DAY_BONES);
		takings.put(Items.COOKED_BEEF, DAY_MEAT);
		takings.put(Items.IRON_NUGGET, DAY_IRON);
		takings.put(Items.ARROW, DAY_ARROWS);
		takings.put(Items.LEATHER, DAY_LEATHER);
		if (Storage.loaded(level, camp)) {
			for (var e : takings.entrySet()) {
				Storage.put(level, camp, e.getKey(), e.getValue() * days);
			}
		}
		Camps.updateData(server, camp.id(), d -> d.withGrown(d.grown() + days));
		Camps.CampData data = Camps.data(server, camp.id());
		if (data == null) {
			return;
		}
		int cap = (int)Math.min(MAX_ORCS, START_GRUNTS + START_RANGERS + data.grown() / DAYS_PER_ORC);
		RandomSource random = level.getRandom();
		BlockPos fire = data.center();
		for (int i = 0; i < days; i++) {
			Faction now = Factions.get(server, camp.id());
			if (now == null || fighters(now) >= cap) {
				break;
			}
			int rangers = now.holdings().count(Orcs.RANGER);
			int role = rangers * RANGER_EVERY < fighters(now) ? Orcs.RANGER : Orcs.GRUNT;
			spawn(level, camp.id(), role, spot(level, fire, random), fire, OrcBuildings.HOME_RADIUS);
		}
	}

	/** Its grunts and rangers on the roster, or on their way back to it (a dead one's place still to fill). */
	static int fighters(Faction camp) {
		Map<String, Integer> vacant = camp.holdings().vacancies();
		return camp.holdings().count(Orcs.GRUNT) + camp.holdings().count(Orcs.RANGER)
			+ vacant.getOrDefault(Folk.roleName(Orcs.GRUNT), 0) + vacant.getOrDefault(Folk.roleName(Orcs.RANGER), 0);
	}

	static boolean wholeCampLoaded(ServerLevel level, BlockPos fire) {
		int r = OrcBuildings.RING_RADIUS;
		for (BlockPos corner : new BlockPos[] {fire, fire.offset(-r, 0, -r), fire.offset(r, 0, -r), fire.offset(-r, 0, r), fire.offset(r, 0, r)}) {
			if (!level.isPositionEntityTicking(corner)) {
				return false;
			}
		}
		return true;
	}

	/** Every {@link Camps#CHECK_TICKS} while a standing camp is loaded: its warchief replaced if he's gone, its next building raised. */
	static void tick(ServerLevel level, Faction camp, Camps.CampData data, boolean force) {
		MinecraftServer server = level.getServer();
		boolean present = camp.leader().isPresent() && level.getEntity(camp.leader().get()) != null;
		if (present || !wholeCampLoaded(level, data.center())) {
			MISSING.remove(camp.id());
		} else if (MISSING.merge(camp.id(), (long)Camps.CHECK_TICKS, Long::sum) >= RESEAT_TICKS) {
			MISSING.remove(camp.id());
			crown(level, camp, OrcBuildings.throne(data.center(), data.gate()), data.gate());
			JaymosKingdoms.LOGGER.info("{} has a new warchief (the last was lost)", camp.name());
		}
		int allowed = Math.min(OrcBuildings.GROWTH.size(), data.grown());
		if (data.built() >= allowed || !Storage.loaded(level, camp)) {
			return;
		}
		IllagerBuildings.Spot spot = OrcBuildings.GROWTH.get(data.built());
		BlockPos where = spot.placer(data.center(), data.gate()).at(0, 0, 0);
		if (!force && Raids.playerNear(level, where, BUILD_HIDE_RANGE)) {
			return;
		}
		if (!clear(level, spot, data.center(), data.gate())) {
			// (something's in the way: that one is skipped)
			Camps.updateData(server, camp.id(), d -> d.withBuilt(d.built() + 1));
			return;
		}
		Map<Item, Integer> cost = Map.of(Items.DARK_OAK_LOG, BUILD_LOGS, Items.WOOL.pick(DyeColor.BLACK), BUILD_HIDES);
		if (!Storage.has(level, camp, cost)) {
			return;
		}
		for (var e : cost.entrySet()) {
			Storage.take(level, camp, e.getKey(), e.getValue());
		}
		OrcBuildings.place(level, spot, data.center(), data.gate());
		Camps.updateData(server, camp.id(), d -> d.withBuilt(d.built() + 1));
		JaymosKingdoms.LOGGER.debug("{} raised a {}", camp.name(), spot.id());
	}

	/** {@code spot}'s footprint above the ground is clear (nothing but air, plants and snow where it would go). */
	private static boolean clear(ServerLevel level, IllagerBuildings.Spot spot, BlockPos fire, Direction gate) {
		CampBuildings.Placer placer = spot.placer(fire, gate);
		for (Blueprint.Placement p : spot.design().get().blocks()) {
			if (p.offset().getY() < 0) {
				continue;
			}
			BlockPos at = placer.at(p.offset());
			if (!level.isLoaded(at)) {
				return false;
			}
			BlockState there = level.getBlockState(at);
			if (!there.isAir() && !there.canBeReplaced()) {
				return false;
			}
		}
		return true;
	}

	static void clear() {
		MISSING.clear();
	}
}
