package com.jaymoskingdoms;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.250) Blossom villages: the pack's Japan. Andy (2026-10-27) pointed at Tax' Village Architect's cherry blossom
 * village (all rights reserved, Forge 1.21.1 only: nothing of it is used). No village is generated among the cherry
 * trees, so one rises there by itself: out in a cherry grove away from every other settlement, out of sight of the
 * player passing by, a ring of minka round a bell, a shrine at its heart, a torii over the road in, a butcher's shop, a
 * hunting lodge and two market stalls, all drawn in the Blossom way ({@link Blossom}, {@link JobHalls}). It is an
 * ordinary {@link FactionType#VILLAGE} from then on (an elder, a shed, a builder crew, its own growth, the Blossom
 * tongue), with a Blossom name.
 * <p>
 * Every settlement in the Blossom land dresses its guards in the ronin harness, the samurai armour of
 * {@link Armory}: a realm's folk guards through {@link Outfits}, and (with MCA) the village's own MCA guards here, in
 * the settlement's colour.
 */
final class BlossomVillages {
	/** How often (ticks) a Blossom village may rise near a player, and the chance each time. */
	static final int RISE_TICKS = 20 * 60;
	static final float RISE_CHANCE = 0.35F;
	/** How far from a player it rises (at least, plus up to), and how near no player may be. */
	static final int SITE_MIN = 72, SITE_SPREAD = 48;
	static final double HIDE_RANGE = 56.0;
	/** No other settlement's seat this near (blocks), and no bell (a world's own village) this near. */
	static final int SPACING = 384, BELL_CLEARANCE = 128;
	/** The village's ground: its radius, how high it's cleared, the most the ground may rise and fall across it. */
	static final int RADIUS = 24, CLEAR_HEIGHT = 18, MAX_SPREAD = 8;
	/** Tries for a site each time. */
	static final int SITE_TRIES = 6;
	/** Who moves in: villagers in each minka, in each job hall, and its iron golems. */
	static final int PER_HOUSE = 2, PER_HALL = 1, GOLEMS = 1;
	/** How often (ticks) the Blossom land's MCA guards are seen to, and how far from the seat. */
	static final int GUARD_TICKS = 20 * 10;
	static final double GUARD_RANGE = 64.0;

	/** Names: two halves, like Hanamura or Kirizawa. */
	private static final String[] NAME_FIRST = {"Hana", "Sakura", "Kiri", "Matsu", "Tsuki", "Kaze", "Shiro", "Taki", "Ishi", "Haru", "Mizu", "Hoshi",
		"Aki", "Yuki", "Kawa", "Mori", "Momo", "Fuji"};
	private static final String[] NAME_SECOND = {"mura", "zawa", "gawa", "yama", "saki", "hara", "dera", "shima", "oka", "tani", "bashi", "no"};
	private static final DyeColor[] COLOURS = {DyeColor.RED, DyeColor.PINK, DyeColor.WHITE, DyeColor.BLACK, DyeColor.MAGENTA};

	/**
	 * Its buildings, in the frame of its bell (the gate side is -z): what, where its front's middle is, which way it
	 * faces (turns clockwise from the gate: 1 for the left side facing in, 3 for the right), and its width.
	 */
	private record Spot(String id, int x, int z, int turns, int width) {
	}

	private static final List<Spot> HOUSES = List.of(new Spot("house", -6, -8, 1, 0), new Spot("house", -6, 4, 1, 0),
		new Spot("house", 6, -8, 3, 0), new Spot("house", 6, 4, 3, 0));
	private static final List<Spot> HALLS = List.of(new Spot("butcher", -6, 15, 1, 7), new Spot("hunting_lodge", 6, 15, 3, 9));
	private static final List<Spot> STALLS = List.of(new Spot("stall", -5, -17, 1, 5), new Spot("stall", 5, -17, 3, 5));
	private static final Spot SHRINE = new Spot("blossom_shrine", 0, 5, 0, 7);
	private static final Spot TORII = new Spot("blossom_torii", 0, -18, 0, 7);
	private static final int[][] LANTERNS = {{-3, -12}, {3, -12}, {-3, -3}, {3, -3}};
	/** The gravel roads: from the torii to the shrine, and across to each pair of houses (x0, z0, x1, z1). */
	private static final int[][] ROADS = {{-1, -20, 1, 4}, {-6, -9, 6, -7}, {-6, 3, 6, 5}};

	// ---- the Blossom town: the same land's capital, at the scale of the great towns ----
	/** A Blossom town's ground: its radius, the most it may rise and fall, and how high it is cleared. */
	static final int TOWN_RADIUS = 42, TOWN_SPREAD = 10, TOWN_CLEAR = 32;
	/** A site with no kingdom's seat this near (blocks) raises a town and its kingdom; nearer one, a village as ever. */
	static final int TOWN_REALM_SPACING = 900;
	/** Its buildings, in the bell's frame as a village's are: two rows of minka each side of the road, the halls at the outer corners. */
	private static final List<Spot> TOWN_HOUSES = List.of(new Spot("house", -4, -28, 1, 0), new Spot("house", 4, -28, 3, 0),
		new Spot("house", -4, -15, 1, 0), new Spot("house", 4, -15, 3, 0), new Spot("house", -4, 11, 1, 0), new Spot("house", 4, 11, 3, 0),
		new Spot("house", -18, -15, 1, 0), new Spot("house", 18, -15, 3, 0), new Spot("house", -18, -2, 1, 0), new Spot("house", 18, -2, 3, 0));
	private static final List<Spot> TOWN_HALLS = List.of(new Spot("butcher", -18, -28, 1, 7), new Spot("hunting_lodge", 18, -28, 3, 9),
		new Spot("fishery", -18, 11, 1, 9), new Spot("training_hall", 18, 11, 3, 9));
	private static final List<Spot> TOWN_STALLS = List.of(new Spot("stall", -5, -4, 1, 5), new Spot("stall", 5, -4, 3, 5),
		new Spot("stall", -5, 3, 1, 5), new Spot("stall", 5, 3, 3, 5));
	/**
	 * The castle's front (where its moat begins) lies this far from the bell, past the halls' row, and its keep's middle is on the
	 * road's line. It is built at the full size of the Ninja Castle build it takes after ({@link Blossom#CASTLE_WIDTH} x
	 * {@link Blossom#CASTLE_DEPTH}), so it has ground of its own: a second levelled circle, {@link #CASTLE_REACH} round the
	 * castle's middle (its far corners are 39 from it), cleared {@link #CASTLE_CLEAR} high, joined to the town's.
	 */
	static final int CASTLE_FRONT = 26, CASTLE_REACH = 42, CASTLE_CLEAR = 46;
	private static final Spot TOWN_KEEP = new Spot("blossom_tenshu", Blossom.CASTLE_WIDTH / 2 - Blossom.CASTLE_MIDDLE, CASTLE_FRONT, 0, Blossom.CASTLE_WIDTH);
	/** The castle's middle in the bell's frame: x across, z ahead. */
	private static final int CASTLE_CX = Blossom.CASTLE_WIDTH / 2 - Blossom.CASTLE_MIDDLE + (Blossom.CASTLE_WIDTH - 1) / 2 - Blossom.CASTLE_WIDTH / 2,
		CASTLE_CZ = CASTLE_FRONT + Blossom.CASTLE_DEPTH / 2;
	private static final Spot TOWN_SHRINE = new Spot("blossom_shrine", -30, 6, 0, 7);
	private static final Spot TOWN_TORII = new Spot("blossom_torii", 0, -36, 0, 7);
	private static final int[][] TOWN_LANTERNS = {{-3, -33}, {3, -33}, {-3, -21}, {3, -21}, {-3, -8}, {3, -8}, {-3, 7}, {3, 7}, {-3, 15}, {3, 15}, {-3, 23}, {3, 23}};
	/** The main road from the torii to the keep, the lanes between the rows, and an alley behind each inner row. */
	private static final int[][] TOWN_ROADS = {{-1, -38, 1, CASTLE_FRONT - 1}, {-17, -22, 17, -21}, {-17, -9, 17, -8}, {-17, 4, 17, 5}, {-17, -34, -16, 17},
		{16, -34, 17, 17}, {-4, -5, 4, 5}};

	/** Settlement id → the land its seat stands in (it never changes). */
	private static final Map<Integer, Climate> CLIMATES = new HashMap<>();

	private BlossomVillages() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BlossomVillages::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CLIMATES.clear());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % RISE_TICKS == 0 && level.getRandom().nextFloat() < RISE_CHANCE) {
			tryRise(level);
		}
		if (now % GUARD_TICKS == 7) {
			dressGuards(level);
		}
	}

	// ---- rising --------------------------------------------------------------------------------------------------------

	/** Looks round a player in a cherry grove for a site, and raises a village there. */
	private static void tryRise(ServerLevel level) {
		List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator() && level.getBiome(p.blockPosition()).is(Biomes.CHERRY_GROVE))
			.toList();
		if (players.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (int tries = 0; tries < SITE_TRIES; tries++) {
			ServerPlayer player = players.get(random.nextInt(players.size()));
			double angle = random.nextDouble() * Math.PI * 2.0;
			int r = SITE_MIN + random.nextInt(SITE_SPREAD);
			BlockPos at = player.blockPosition().offset((int)Math.round(Math.cos(angle) * r), 0, (int)Math.round(Math.sin(angle) * r));
			if (!open(level, at)) {
				continue;
			}
			int floor = IllagerBuildings.floor(level, at, MAX_SPREAD, RADIUS);
			if (floor == Integer.MIN_VALUE) {
				continue;
			}
			// the torii faces the way the player came from
			Direction gate = Direction.getApproximateNearest(player.getX() - at.getX(), 0, player.getZ() - at.getZ());
			// with no kingdom in reach and room enough, the land's capital; else a village
			int wide = townSite(level, at) ? IllagerBuildings.floor(level, at, TOWN_SPREAD, TOWN_RADIUS) : Integer.MIN_VALUE;
			if (wide != Integer.MIN_VALUE) {
				// the castle's ground, beyond the town's, must be loaded and level enough as well
				BlockPos castle = new CampBuildings.Placer(at, gate, 0).at(CASTLE_CX, 0, CASTLE_CZ);
				int castleFloor = IllagerBuildings.floor(level, castle, TOWN_SPREAD, CASTLE_REACH);
				if (castleFloor == Integer.MIN_VALUE || Math.abs(castleFloor - wide) > TOWN_SPREAD) {
					wide = Integer.MIN_VALUE;
				}
			}
			if (wide != Integer.MIN_VALUE && foundTown(level, at, wide, gate) != null) {
				return;
			}
			found(level, at, floor, gate);
			return;
		}
	}

	/** {@code at} is free for a Blossom village: loaded, a cherry grove all round, nobody's land, no settlement or bell near, out of sight. */
	private static boolean open(ServerLevel level, BlockPos at) {
		MinecraftServer server = level.getServer();
		if (!level.isPositionEntityTicking(at)) {
			return false;
		}
		for (BlockPos corner : new BlockPos[] {at, at.offset(-RADIUS, 0, -RADIUS), at.offset(RADIUS, 0, -RADIUS), at.offset(-RADIUS, 0, RADIUS),
			at.offset(RADIUS, 0, RADIUS)}) {
			if (!level.isLoaded(corner) || !level.getBiome(corner).is(Biomes.CHERRY_GROVE) || Factions.at(server, level, corner) != null) {
				return false;
			}
		}
		for (Faction f : Factions.all(server)) {
			if (Raids.horizontal(f.seat(), at) < (double)SPACING * SPACING) {
				return false;
			}
		}
		if (Raids.playerNear(level, at, HIDE_RANGE)) {
			return false;
		}
		return level.getPoiManager().findClosest(holder -> holder.is(PoiTypes.MEETING), at, BELL_CLEARANCE, PoiManager.Occupancy.ANY).isEmpty();
	}

	/** {@code at} (already {@link #open}) could hold a town: its whole ground loaded, and no kingdom's seat within {@link #TOWN_REALM_SPACING}. */
	private static boolean townSite(ServerLevel level, BlockPos at) {
		if (!level.hasChunksAt(at.getX() - TOWN_RADIUS, at.getZ() - TOWN_RADIUS, at.getX() + TOWN_RADIUS, at.getZ() + TOWN_RADIUS)) {
			return false;
		}
		for (Faction f : Factions.all(level.getServer())) {
			if (!f.fallen() && f.type().isRealm() && Raids.horizontal(f.seat(), at) < (double)TOWN_REALM_SPACING * TOWN_REALM_SPACING) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A Blossom town round {@code center} on {@code floor}, and its kingdom: a road from the torii through the bell plaza
	 * to a castle keep in the Blossom way ({@link Blossom#tenshu}), ten minka in two rows each side, four job halls, four
	 * stalls round the bell, a shrine beside the keep. The keep is the seat (a throne at the back of its great room, a
	 * panic room under it, archers' posts), the kingdom has a Blossom name and is manned like any other, and its builders
	 * grow the town from there. Null (and nothing built) never: the site was checked before.
	 */
	static @Nullable Faction foundTown(ServerLevel level, BlockPos center, int floor, Direction gate) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		BlockPos bell = new BlockPos(center.getX(), floor, center.getZ());
		CampBuildings.Placer frame = new CampBuildings.Placer(bell, gate, 0);
		CampBuildings.level(level, bell, TOWN_RADIUS, TOWN_CLEAR);
		// the castle's own ground, widened past the town's to hold a keep at the full size of its model
		CampBuildings.level(level, frame.at(CASTLE_CX, 0, CASTLE_CZ), CASTLE_REACH, CASTLE_CLEAR);
		for (int[] road : TOWN_ROADS) {
			for (int x = road[0]; x <= road[2]; x++) {
				for (int z = road[1]; z <= road[3]; z++) {
					level.setBlock(frame.at(x, -1, z), Blocks.GRAVEL.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				level.setBlock(frame.at(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		level.setBlock(bell, Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
		Faction kingdom = Factions.update(server, KingdomEvents.create(level, bell).id(), f -> f.withName(name(server, random)));
		if (kingdom == null) {
			return null;
		}
		int id = kingdom.id();
		CLIMATES.put(id, Climate.CHERRY);
		List<Works.Built> built = new ArrayList<>();
		// the keep first: it is the seat
		List<Works.Built> keepBuilt = new ArrayList<>();
		place(level, Blossom.tenshu(kit(level, kingdom, random)), TOWN_KEEP, bell, gate, keepBuilt);
		Works.Built tenshu = keepBuilt.getFirst();
		BoundingBox keepBox = BoundingBox.fromCorners(tenshu.min(), tenshu.max());
		place(level, Blossom.shrine(), TOWN_SHRINE, bell, gate, null);
		place(level, Blossom.torii(), TOWN_TORII, bell, gate, null);
		for (int[] lantern : TOWN_LANTERNS) {
			place(level, Blossom.stoneLantern(), new Spot("blossom_lantern", lantern[0], lantern[1], 0, 1), bell, gate, null);
		}
		List<BlockPos> homes = new ArrayList<>(), halls = new ArrayList<>();
		for (Spot s : TOWN_HOUSES) {
			Blueprint house = Blossom.minka(kit(level, kingdom, random));
			homes.add(place(level, house, new Spot(s.id(), s.x(), s.z(), s.turns(), house.width()), bell, gate, built));
		}
		for (Spot s : TOWN_HALLS) {
			Buildings.Kit k = kit(level, kingdom, random);
			Blueprint hall = switch (s.id()) {
				case "butcher" -> JobHalls.butcher(k);
				case "fishery" -> JobHalls.fishery(k);
				case "training_hall" -> JobHalls.trainingHall(k);
				default -> JobHalls.huntingLodge(k);
			};
			halls.add(place(level, hall, new Spot(s.id(), s.x(), s.z(), s.turns(), hall.width()), bell, gate, built));
		}
		for (Spot s : TOWN_STALLS) {
			place(level, Buildings.stall(kit(level, kingdom, random)), s, bell, gate, built);
		}
		Works.update(server, id, w -> {
			List<Works.Built> all = new ArrayList<>(w.built());
			all.addAll(built);
			return w.withBuilt(all);
		});
		// the seat in the keep's great room; without one (it couldn't be read) the kingdom's own castle beside the town
		Seats.Room room = Seats.room(level, new Seats.Building(keepBox, "blossom_tenshu"), bell);
		Castle.Keep keep;
		if (room != null) {
			keep = Seats.keep(level, kingdom, room, bell, true, true);
			Holds.guard(server, id, keepBox);
		} else {
			keep = Castle.build(level, kingdom, bell, Castle.Kind.KEEP);
		}
		Factions.putKeep(server, id, keep);
		KingdomEvents.man(level, Factions.get(server, id), keep);
		Storehouse.build(level, Factions.get(server, id));
		KingdomEvents.settle(level, Factions.get(server, id), null, bell);
		for (BlockPos home : homes) {
			for (int i = 0; i < PER_HOUSE; i++) {
				EntityTypes.VILLAGER.spawn(level, Construction.standable(level, home, 3), EntitySpawnReason.STRUCTURE);
			}
		}
		for (BlockPos hall : halls) {
			for (int i = 0; i < PER_HALL; i++) {
				EntityTypes.VILLAGER.spawn(level, Construction.standable(level, hall, 3), EntitySpawnReason.STRUCTURE);
			}
		}
		WalledTowns.front(server, id, gate);
		JaymosKingdoms.LOGGER.info("A Blossom town, {}, rose at {}; its keep's throne at {}", kingdom.name(), bell, keep.throne());
		return Factions.get(server, id);
	}

	/** A new Blossom village round {@code center} on {@code floor}, its torii toward {@code gate}: built, named, peopled. */
	static Faction found(ServerLevel level, BlockPos center, int floor, Direction gate) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		BlockPos bell = new BlockPos(center.getX(), floor, center.getZ());
		CampBuildings.level(level, bell, RADIUS, CLEAR_HEIGHT);
		CampBuildings.Placer frame = new CampBuildings.Placer(bell, gate, 0);
		// the roads, then the bell on its stone
		for (int[] road : ROADS) {
			for (int x = road[0]; x <= road[2]; x++) {
				for (int z = road[1]; z <= road[3]; z++) {
					level.setBlock(frame.at(x, -1, z), Blocks.GRAVEL.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				level.setBlock(frame.at(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		level.setBlock(bell, Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
		Faction village = Faction.create(Factions.nextId(server), FactionType.VILLAGE, name(server, random), COLOURS[random.nextInt(COLOURS.length)],
			bell, KingdomEvents.claim(server, bell, Villages.CLAIM_RADIUS));
		Factions.put(server, village);
		int id = village.id();
		// what's drawn the same every time, then what's drawn from the village's own kit
		place(level, Blossom.shrine(), SHRINE, bell, gate, null);
		place(level, Blossom.torii(), TORII, bell, gate, null);
		for (int[] lantern : LANTERNS) {
			place(level, Blossom.stoneLantern(), new Spot("blossom_lantern", lantern[0], lantern[1], 0, 1), bell, gate, null);
		}
		List<BlockPos> homes = new ArrayList<>(), halls = new ArrayList<>();
		List<Works.Built> built = new ArrayList<>();
		for (Spot s : HOUSES) {
			Blueprint house = Blossom.minka(kit(level, village, random));
			homes.add(place(level, house, new Spot(s.id(), s.x(), s.z(), s.turns(), house.width()), bell, gate, built));
		}
		for (Spot s : HALLS) {
			Buildings.Kit k = kit(level, village, random);
			halls.add(place(level, s.id().equals("butcher") ? JobHalls.butcher(k) : JobHalls.huntingLodge(k), s, bell, gate, built));
		}
		for (Spot s : STALLS) {
			place(level, Buildings.stall(kit(level, village, random)), s, bell, gate, built);
		}
		// counted among its buildings, so it grows from here (and its job halls are its jobs' workplaces)
		Works.update(server, id, w -> {
			List<Works.Built> all = new ArrayList<>(w.built());
			all.addAll(built);
			return w.withBuilt(all);
		});
		// its shed and builder crew, like any village's
		Storehouse.build(level, Factions.get(server, id));
		// its people: villagers in the houses and the halls, a golem by the bell
		for (BlockPos home : homes) {
			for (int i = 0; i < PER_HOUSE; i++) {
				EntityTypes.VILLAGER.spawn(level, Construction.standable(level, home, 3), EntitySpawnReason.STRUCTURE);
			}
		}
		for (BlockPos hall : halls) {
			for (int i = 0; i < PER_HALL; i++) {
				EntityTypes.VILLAGER.spawn(level, Construction.standable(level, hall, 3), EntitySpawnReason.STRUCTURE);
			}
		}
		for (int i = 0; i < GOLEMS; i++) {
			EntityTypes.IRON_GOLEM.spawn(level, Construction.standable(level, frame.at(0, 0, -6), 3), EntitySpawnReason.STRUCTURE);
		}
		Faction stored = Factions.get(server, id);
		if (stored != null) {
			Villages.chooseElder(level, stored);
		}
		CLIMATES.put(id, Climate.CHERRY);
		// (0.7.490) its torii is its front, for the walls it will raise
		WalledTowns.front(server, id, gate);
		JaymosKingdoms.LOGGER.info("A Blossom village, {}, rose at {}", village.name(), bell);
		Faction out = Factions.get(server, id);
		return out != null ? out : village;
	}

	/** One building's kit from the village's own look, its materials and details rolled (never the plain design). */
	private static Buildings.Kit kit(ServerLevel level, Faction village, RandomSource random) {
		long variant = random.nextLong();
		return Buildings.Kit.of(level, village, variant == 0L ? 1L : variant, variant == 0L ? 1L : variant);
	}

	/**
	 * Places {@code blueprint} at once at {@code spot} (its y = 0, the floor laid in place of the ground, on the ground's
	 * top), turned to face its way, its blocks joining up as they go; added to {@code built} (unless null) as one of the
	 * village's buildings. Returns the middle of its floor.
	 */
	private static BlockPos place(ServerLevel level, Blueprint blueprint, Spot spot, BlockPos bell, Direction gate, @Nullable List<Works.Built> built) {
		CampBuildings.Placer frame = new CampBuildings.Placer(bell, gate, 0);
		Direction facing = gate;
		for (int i = 0; i < spot.turns(); i++) {
			facing = facing.getClockWise();
		}
		CampBuildings.Placer placer = new CampBuildings.Placer(frame.at(spot.x(), -1, spot.z()), facing, spot.width());
		for (Blueprint.Placement p : blueprint.blocks()) {
			level.setBlock(placer.at(p.offset()), placer.turn(p.state()), Block.UPDATE_ALL);
		}
		if (built != null) {
			BlockPos a = placer.at(0, 0, 0), b = placer.at(blueprint.width() - 1, blueprint.height() - 1, blueprint.depth() - 1);
			built.add(new Works.Built(spot.id(), new BlockPos(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ())),
				new BlockPos(Math.max(a.getX(), b.getX()), b.getY(), Math.max(a.getZ(), b.getZ()))));
		}
		return placer.at(blueprint.width() / 2, 1, blueprint.depth() / 2);
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

	// ---- the Blossom land's guards -----------------------------------------------------------------------------------------

	/** With MCA: every settlement among the cherry trees puts its MCA guards in the ronin harness, in its colour. */
	private static void dressGuards(ServerLevel level) {
		if (!McaCompat.LOADED) {
			return;
		}
		MinecraftServer server = level.getServer();
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat()) || climate(level, f) != Climate.CHERRY) {
				continue;
			}
			DyeColor colour = f.styleColor(server);
			for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(GUARD_RANGE),
				v -> v.isAlive() && !v.isBaby() && McaCompat.isGuard(v) && !Outfits.wearsRonin(v))) {
				Outfits.ronin(v, colour);
			}
		}
	}

	/** (0.7.490) {@code f} stands among the cherry trees: it walls itself in the Blossom way ({@link WalledTowns}). */
	static boolean blossom(ServerLevel level, Faction f) {
		return climate(level, f) == Climate.CHERRY;
	}

	private static Climate climate(ServerLevel level, Faction f) {
		return CLIMATES.computeIfAbsent(f.id(), x -> Climate.at(level, f.seat()));
	}

	// ---- the command -------------------------------------------------------------------------------------------------------

	/** {@code /kingdom found blossom}: a Blossom village round you, its torii the way you face. */
	static int foundHere(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = Factions.level(c.getSource().getServer());
		if (player.level() != level) {
			c.getSource().sendFailure(Component.literal("Blossom villages are only in the Overworld"));
			return 0;
		}
		BlockPos at = player.blockPosition();
		Faction village = found(level, at, at.getY(), player.getDirection());
		c.getSource().sendSuccess(() -> Component.literal("Founded the Blossom village of " + village.name()), true);
		return 1;
	}

	/** {@code /kingdom found blossomtown}: a Blossom town and its kingdom round you, its torii behind you and its keep ahead. */
	static int foundTownHere(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = Factions.level(c.getSource().getServer());
		if (player.level() != level) {
			c.getSource().sendFailure(Component.literal("Blossom towns are only in the Overworld"));
			return 0;
		}
		BlockPos at = player.blockPosition();
		Faction town = foundTown(level, at, at.getY(), player.getDirection().getOpposite());
		if (town == null) {
			c.getSource().sendFailure(Component.literal("No Blossom town could be founded here"));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Founded the Blossom kingdom of " + town.name()), true);
		return 1;
	}
}
