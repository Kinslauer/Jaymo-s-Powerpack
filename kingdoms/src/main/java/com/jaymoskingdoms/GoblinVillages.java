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
 * Goblin villages: a third kind of camp ({@link FactionType#BARBARIAN} factions whose {@link Camps.CampData#kind} is
 * {@link #KIND}), riding on the camps' machinery. One may rise in the wild near a kingdom (in woods, plains, swamps and
 * jungles), a stockade of mushroom-capped huts round a bonfire, with a king on his throne in the great hut, goblins
 * about the place, drinkers in the tavern and slingers on the lookout ({@link GoblinBuildings}, {@link Goblins}). It
 * grows daily by its own hands (more goblins, more huts), and it doesn't raid: it's somewhere to go. Kill its king and it
 * is cleared like any camp ({@link Camps#chiefSlain}). A player in a goblin mask can walk in among them ({@link Disguise}).
 */
final class GoblinVillages {
	/** {@link Camps.CampData#kind} of a goblin village. */
	static final String KIND = "goblin";
	/** At most this many standing goblin villages near a kingdom (within {@link Camps#CAMP_AREA}); the chance one rises, each spawn check. */
	static final int VILLAGES_PER_KINGDOM = 1;
	static final float VILLAGE_CHANCE = 0.12F;
	/** The most the ground may rise and fall across a village. */
	static final int MAX_SPREAD = 5;
	/** A new village's people, and how it grows: another goblin every so many days, every so many of them a slinger, up to a most. */
	static final int START_GOBLINS = 6, START_DRINKERS = 2, START_SLINGERS = 2, DAYS_PER_GOBLIN = 2, SLINGER_EVERY = 4, MAX_GOBLINS = 16;
	/** What a village takes in each day by its own hands (foraging, felling, digging, pilfering). */
	static final int DAY_LOGS = 6, DAY_MUD = 6, DAY_MUSHROOMS = 6, DAY_NUGGETS = 4, DAY_BREAD = 3, DAY_ARROWS = 6, DAY_BONES = 2;
	/** The junk heap a new village starts with. */
	static final int START_LOGS = 24, START_MUD = 16, START_MUSHROOMS = 12, START_ARROWS = 24, START_NUGGETS = 12, START_EMERALDS = 3;
	/** Raising a building: one a day at most, from this much of its heap; never with a player this near (a hut doesn't appear before your eyes). */
	static final int BUILD_LOGS = 4, BUILD_MUD = 6;
	static final double BUILD_HIDE_RANGE = 24.0;
	/** The king keeps this near his throne; drinkers to their tavern; a slinger to the lookout. */
	static final int KING_HOME_RADIUS = 4, DRINKER_RADIUS = 3, SLINGER_RADIUS = 2;
	/** A king missing (his village loaded, him nowhere) this long (ticks) is replaced. */
	static final long RESEAT_TICKS = 20 * 120;

	private static final String[] NAME_FIRST = {"Snag", "Grub", "Muck", "Rot", "Gnash", "Skulk", "Bog", "Wart", "Mold", "Grot", "Snot", "Fester",
		"Nib", "Scab", "Gob", "Sludge"};
	private static final String[] NAME_SECOND = {"hollow", "pit", "warren", "mire", "burrow", "heap", "stew", "nook", "gulch", "midden", "den",
		"bottom"};
	private static final DyeColor[] COLOURS = {DyeColor.GREEN, DyeColor.LIME, DyeColor.BROWN};

	/** Village id → ticks its king has been missing. */
	private static final Map<Integer, Long> MISSING = new HashMap<>();

	private GoblinVillages() {
	}

	static boolean is(MinecraftServer server, int camp) {
		Camps.CampData data = Camps.data(server, camp);
		return data != null && KIND.equals(data.kind());
	}

	/** Goblin country: woods, plains, swamps and jungles (not deserts, snow, mountains or the sea). */
	static boolean land(ServerLevel level, BlockPos at) {
		Holder<Biome> biome = level.getBiome(at);
		return biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE) || biome.is(Biomes.PLAINS)
			|| biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(Biomes.MEADOW) || biome.is(Biomes.SUNFLOWER_PLAINS);
	}

	// ---- founding ------------------------------------------------------------------------------------------------------

	/** A new goblin village at {@code center} (its fire on {@code floor}), its gate facing {@code gate}: built, its king seated, peopled. */
	static Faction found(ServerLevel level, BlockPos center, int floor, Direction gate) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		BlockPos fire = new BlockPos(center.getX(), floor, center.getZ());
		Faction village = Faction.create(Factions.nextId(server), FactionType.BARBARIAN, name(server, random), COLOURS[random.nextInt(COLOURS.length)], fire,
			KingdomEvents.claim(server, fire, Camps.CLAIM_RADIUS));
		Factions.put(server, village);
		int id = village.id();
		long now = level.getGameTime();
		// (its data first: everything after asks what kind of camp it is)
		Camps.putData(server, id, new Camps.CampData(fire, gate, now, now, Production.day(server), -1L, List.of(), 0L, Map.of(), KIND, 0, 0));
		GoblinBuildings.Layout layout = GoblinBuildings.build(level, center, floor, gate);
		Storage.register(server, id, layout.storage(), layout.depot());
		Faction stocked = Factions.get(server, id);
		Storage.put(level, stocked, Items.SPRUCE_LOG, START_LOGS);
		Storage.put(level, stocked, Items.MUD_BRICKS, START_MUD);
		Storage.put(level, stocked, Items.BROWN_MUSHROOM, START_MUSHROOMS);
		Storage.put(level, stocked, Items.ARROW, START_ARROWS);
		Storage.put(level, stocked, Items.GOLD_NUGGET, START_NUGGETS);
		Storage.put(level, stocked, Items.EMERALD, START_EMERALDS);
		Camps.takeSides(server, id, KIND);
		crown(level, Factions.get(server, id), layout.throne(), gate);
		// slingers on the lookout and up the gate towers, drinkers in the tavern, the rest about the fire
		spawn(level, id, Goblins.SLINGER, layout.lookout(), layout.lookout(), SLINGER_RADIUS);
		for (int i = 1; i < START_SLINGERS; i++) {
			int side = i % 2 == 1 ? 1 : -1;
			BlockPos tower = new CampBuildings.Placer(fire, gate, 0)
				.at(side * (GoblinBuildings.GATE_HALF + 2), GoblinBuildings.TOWER_HIGH + 1, -GoblinBuildings.RING_RADIUS + 1);
			spawn(level, id, Goblins.SLINGER, tower, tower, 1);
		}
		for (int i = 0; i < START_DRINKERS; i++) {
			spawn(level, id, Goblins.GOBLIN, layout.tavern(), layout.tavern(), DRINKER_RADIUS);
		}
		for (int i = START_DRINKERS; i < START_GOBLINS; i++) {
			spawn(level, id, Goblins.GOBLIN, spot(level, fire, random), fire, GoblinBuildings.HOME_RADIUS);
		}
		// (0.7.329) and scalebane sellswords
		ScalebaneArcher.hire(level, id, fire, ScalebaneArcher.START);
		// (0.7.335) and its war barrel by the gate
		WarBarrel.hire(level, id);
		JaymosKingdoms.LOGGER.info("A goblin village, {}, rose at {}", village.name(), fire);
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

	/** Seats a new king on the throne and makes him the village's leader. Returns the village as stored. */
	static @Nullable Faction crown(ServerLevel level, @Nullable Faction village, BlockPos throne, Direction gate) {
		if (village == null) {
			return null;
		}
		Folk king = make(level, village, Goblins.KING, throne, throne, KING_HOME_RADIUS);
		if (king != null) {
			king.snapTo(throne.getX() + 0.5, throne.getY(), throne.getZ() + 0.5, gate.toYRot(), 0.0F);
		}
		return Factions.update(level.getServer(), village.id(), f -> f.withLeader(king == null ? Optional.empty() : Optional.of(king.getUUID())));
	}

	/** One of the village's goblins at {@code at}, keeping {@code radius} of {@code home}, counted on its roster. */
	static @Nullable Folk spawn(ServerLevel level, int id, int role, BlockPos at, BlockPos home, int radius) {
		Faction village = Factions.get(level.getServer(), id);
		if (village == null) {
			return null;
		}
		Folk goblin = make(level, village, role, at, home, radius);
		if (goblin != null) {
			Factions.update(level.getServer(), id, f -> f.withHoldings(f.holdings().adjust(role, 1)));
		}
		return goblin;
	}

	private static @Nullable Folk make(ServerLevel level, Faction village, int role, BlockPos at, BlockPos home, int radius) {
		Folk goblin = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.STRUCTURE);
		if (goblin == null) {
			return null;
		}
		goblin.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		goblin.setup(role, village, home, radius);
		level.addFreshEntity(goblin);
		return goblin;
	}

	/** Open ground on the village's floor near its fire (not in a hut), for one of its goblins to stand. */
	static BlockPos spot(ServerLevel level, BlockPos fire, RandomSource random) {
		for (int tries = 0; tries < 16; tries++) {
			BlockPos column = fire.offset(random.nextInt(15) - 7, 0, random.nextInt(15) - 7);
			if (column.distManhattan(fire) < 3 || !level.isLoaded(column)) {
				continue;
			}
			BlockPos at = ground(level, column);
			if (at.getY() == fire.getY() && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
				return at;
			}
		}
		return fire.offset(2, 0, 3);
	}

	private static BlockPos ground(ServerLevel level, BlockPos column) {
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
	}

	// ---- growing ---------------------------------------------------------------------------------------------------------

	/** {@code days}' growth: what the village takes in by its own hands, and (as it ages) more goblins. */
	static void grow(ServerLevel level, Faction village, int days) {
		MinecraftServer server = level.getServer();
		Map<Item, Integer> takings = new LinkedHashMap<>();
		takings.put(Items.SPRUCE_LOG, DAY_LOGS);
		takings.put(Items.MUD_BRICKS, DAY_MUD);
		takings.put(Items.BROWN_MUSHROOM, DAY_MUSHROOMS / 2);
		takings.put(Items.RED_MUSHROOM, DAY_MUSHROOMS / 2);
		takings.put(Items.GOLD_NUGGET, DAY_NUGGETS);
		takings.put(Items.BREAD, DAY_BREAD);
		takings.put(Items.ARROW, DAY_ARROWS);
		takings.put(Items.BONE, DAY_BONES);
		if (Storage.loaded(level, village)) {
			for (var e : takings.entrySet()) {
				Storage.put(level, village, e.getKey(), e.getValue() * days);
			}
		}
		Camps.updateData(server, village.id(), d -> d.withGrown(d.grown() + days));
		Camps.CampData data = Camps.data(server, village.id());
		if (data == null) {
			return;
		}
		long age = data.grown();
		int cap = (int)Math.min(MAX_GOBLINS, START_GOBLINS + START_SLINGERS + age / DAYS_PER_GOBLIN);
		RandomSource random = level.getRandom();
		BlockPos fire = data.center();
		for (int i = 0; i < days; i++) {
			Faction now = Factions.get(server, village.id());
			if (now == null || goblins(now) >= cap) {
				break;
			}
			int slingers = now.holdings().count(Goblins.SLINGER);
			int role = slingers * SLINGER_EVERY < goblins(now) ? Goblins.SLINGER : Goblins.GOBLIN;
			spawn(level, village.id(), role, spot(level, fire, random), fire, GoblinBuildings.HOME_RADIUS);
		}
	}

	/** Its goblins on the roster, or on their way back to it (a dead one's place still to fill). */
	static int goblins(Faction village) {
		Map<String, Integer> vacant = village.holdings().vacancies();
		return village.holdings().count(Goblins.GOBLIN) + village.holdings().count(Goblins.SLINGER)
			+ vacant.getOrDefault(Folk.roleName(Goblins.GOBLIN), 0) + vacant.getOrDefault(Folk.roleName(Goblins.SLINGER), 0);
	}

	static boolean wholeVillageLoaded(ServerLevel level, BlockPos fire) {
		int r = GoblinBuildings.RING_RADIUS;
		for (BlockPos corner : new BlockPos[] {fire, fire.offset(-r, 0, -r), fire.offset(r, 0, -r), fire.offset(-r, 0, r), fire.offset(r, 0, r)}) {
			if (!level.isPositionEntityTicking(corner)) {
				return false;
			}
		}
		return true;
	}

	/** Every {@link Camps#CHECK_TICKS} while a standing village is loaded: its king replaced if he's gone, its next building raised. */
	static void tick(ServerLevel level, Faction village, Camps.CampData data, boolean force) {
		MinecraftServer server = level.getServer();
		boolean present = village.leader().isPresent() && level.getEntity(village.leader().get()) != null;
		if (present || !wholeVillageLoaded(level, data.center())) {
			MISSING.remove(village.id());
		} else if (MISSING.merge(village.id(), (long)Camps.CHECK_TICKS, Long::sum) >= RESEAT_TICKS) {
			MISSING.remove(village.id());
			crown(level, village, GoblinBuildings.throne(data.center(), data.gate()), data.gate());
			JaymosKingdoms.LOGGER.info("{} has a new goblin king (the last was lost)", village.name());
		}
		int allowed = Math.min(GoblinBuildings.GROWTH.size(), data.grown());
		if (data.built() >= allowed || !Storage.loaded(level, village)) {
			return;
		}
		IllagerBuildings.Spot spot = GoblinBuildings.GROWTH.get(data.built());
		BlockPos where = spot.placer(data.center(), data.gate()).at(0, 0, 0);
		if (!force && Raids.playerNear(level, where, BUILD_HIDE_RANGE)) {
			return;
		}
		if (!clear(level, spot, data.center(), data.gate())) {
			// (something's in the way: that one is skipped)
			Camps.updateData(server, village.id(), d -> d.withBuilt(d.built() + 1));
			return;
		}
		Map<Item, Integer> cost = Map.of(Items.SPRUCE_LOG, BUILD_LOGS, Items.MUD_BRICKS, BUILD_MUD);
		if (!Storage.has(level, village, cost)) {
			return;
		}
		for (var e : cost.entrySet()) {
			Storage.take(level, village, e.getKey(), e.getValue());
		}
		GoblinBuildings.place(level, spot, data.center(), data.gate());
		Camps.updateData(server, village.id(), d -> d.withBuilt(d.built() + 1));
		JaymosKingdoms.LOGGER.debug("{} raised a {}", village.name(), spot.id());
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
