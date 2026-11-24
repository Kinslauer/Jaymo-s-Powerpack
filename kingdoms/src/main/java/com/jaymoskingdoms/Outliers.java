package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Buildings that stand alone out in the land (Reference Mods patch 10, V15-V20), raised near a player the way
 * {@link VillageStyles} raise villages, each with its reason for being where it is:
 * <ul>
 * <li><b>Homes out by the work</b> (V15): a farmhouse with its wheat field, a shepherd's hut with its pasture and sheep, a
 * woodcutter's cabin with its woodpile, each {@link #HOME_MIN} to {@link #HOME_MAX} blocks from a settlement (a walk out and
 * back in a day), in the land that job needs (open farmland, grass, standing forest), lived in by a villager of that job.
 * Why: a worker lives where the work is, and the work is not inside the village's walls.</li>
 * <li><b>A roadside trading post</b> (V16): beside the middle of a long King's Road ({@link Roads}), a stall under a roof with
 * a wandering trader behind its counter. Why: on a road of {@link #LONG_ROAD} blocks or more, the middle is a day's walk from
 * either town, and a trader who sells to those who pass wants to be where they stop.</li>
 * <li><b>Watchtowers on both sides</b> (V17): a village's tower with a guard and a signal fire, on the rise between the
 * village and the nearest camp, which lights the fire and calls the village's guards ({@link AlarmBells#answer}) when a raider
 * comes near; an illager camp's lookout on the rise toward the nearest settlement, whose sentry cries out and sets the
 * camp's illagers on a player he sees first. Why: each side watches the way the other comes from, from the high ground.</li>
 * <li><b>The evoker's frozen tower</b> (V19): in snowy taiga only, and rarely: a tower of stone, ice and snow with a lone evoker,
 * his library and his laboratory. Why: he hides his studies from his own kind, where no raider goes for the cold.</li>
 * <li><b>An exile's cabin</b> (V20): in dark forest at least {@link #EXILE_CLEAR} blocks from any settlement, a cabin with one
 * vindicator. Why: he was driven out of his camp, and nobody else lives where the trees shut out the sky.</li>
 * </ul>
 * Each is built once, remembered in {@link #SITES} (for spacing and for the towers' watch).
 */
final class Outliers {
	/** How often (ticks) one may rise near a player, and the chance each time. */
	static final int RISE_TICKS = 20 * 100;
	static final float RISE_CHANCE = 0.3F;
	/** Kinds tried each time. */
	static final int TRIES = 4;
	/** No player this near (blocks); no other outlying building this near; none of the same kind this near. */
	static final double HIDE_RANGE = 48.0, APART = 70.0, APART_SAME = 160.0;
	/** A home out by the work: a settlement within this of the player is looked at, and the home stands this far out from its seat. */
	static final int VILLAGE_REACH = 140, HOME_MIN = 48, HOME_MAX = 84;
	/** Most outlying homes to a settlement. */
	static final int HOMES_PER_VILLAGE = 3;
	/** Trees (of 16 points round) a woodcutter's cabin needs about it; and a deep forest's, for an exile's. */
	static final int TREES_WOOD = 5, TREES_DEEP = 9;
	/** A road this long (blocks) has a middle worth a post; a post stands this far off the road; no settlement's seat this near. */
	static final double LONG_ROAD = 300.0;
	static final int POST_BESIDE = 7, POST_CLEAR = 110, POST_APART = 220;
	/** A watchtower stands this far (min, spread) out from its settlement or camp toward the other side, on the highest ground within {@link #HIGH_LOOK}. */
	static final int TOWER_OUT = 46, TOWER_SPREAD = 16, HIGH_LOOK = 12, TOWER_REACH = 900;
	/** A village's tower's guard sees a raider this far; the fire's smoke calls the village; a fire left to burn this long (ticks) after the last. */
	static final double WARN_RANGE = 56.0;
	static final long DOUSE_TICKS = 20L * 20, ALERT_GAP = 20L * 12, LOOKOUT_GAP = 20L * 30;
	/** A lookout's sentry sees a player this far; he calls illagers this far round. */
	static final double SPOT_RANGE = 42.0, CALL_RANGE = 64.0;
	/** Towers are watched this often (ticks), for players within this range. */
	static final int WATCH_TICKS = 20;
	static final double WATCH_RANGE = 128.0;
	/** The evoker's tower: the chance (when a snowy taiga site is found), and no other this near (blocks). */
	static final float EVOKER_CHANCE = 0.25F;
	static final double EVOKER_APART = 1500.0;
	/** An exile's cabin: no settlement this near (blocks); the same kind no nearer. */
	static final double EXILE_CLEAR = 220.0, EXILE_APART = 500.0;

	enum Kind {
		FARMHOUSE("farmhouse"), SHEPHERD("shepherd"), WOODCUTTER("woodcutter"), TRADING_POST("trading_post"), VILLAGE_TOWER("village_tower"),
		LOOKOUT("illager_lookout"), EVOKER_TOWER("evoker_tower"), EXILE_CABIN("exile_cabin");

		final String id;

		Kind(String id) {
			this.id = id;
		}

		boolean home() {
			return this == FARMHOUSE || this == SHEPHERD || this == WOODCUTTER;
		}
	}

	/** "x,z" of each outlying building to "kind:settlement id:y": what stands where (a tower's is its fire's column and height). */
	static final AttachmentType<Map<String, String>> SITES = AttachmentRegistry.create(JaymosKingdoms.id("outlying_sites"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));

	/** Tower fire position (packed) to the game time it last called for help, and the time the fire was last wanted. */
	private static final Map<Long, Long> ALERTED = new HashMap<>(), WANTED = new HashMap<>();

	private Outliers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Outliers::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ALERTED.clear();
			WANTED.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % RISE_TICKS == 0 && level.getDifficulty() != Difficulty.PEACEFUL && level.getRandom().nextFloat() < RISE_CHANCE) {
			rise(level);
		}
		if (now % WATCH_TICKS == 0) {
			watch(level, now);
		}
	}

	// ---- the record ----------------------------------------------------------------------------------------------------

	private static Map<String, String> sites(ServerLevel level) {
		return level.getAttachedOrElse(SITES, Map.of());
	}

	private static String key(BlockPos at) {
		return at.getX() + "," + at.getZ();
	}

	private static BlockPos keyPos(String key, int y) {
		String[] p = key.split(",");
		return new BlockPos(Integer.parseInt(p[0]), y, Integer.parseInt(p[1]));
	}

	private static void mark(ServerLevel level, BlockPos at, Kind kind, int settlement) {
		Map<String, String> next = new HashMap<>(sites(level));
		next.put(key(at), kind.id + ":" + settlement + ":" + at.getY());
		level.setAttached(SITES, Map.copyOf(next));
	}

	/** Outlying buildings (of {@code kind}, or any if null) standing within {@code range} of {@code at}. */
	private static int near(ServerLevel level, BlockPos at, @Nullable Kind kind, double range) {
		int n = 0;
		for (var e : sites(level).entrySet()) {
			BlockPos p = keyPos(e.getKey(), at.getY());
			if (Raids.horizontal(p, at) <= range * range && (kind == null || e.getValue().startsWith(kind.id + ":"))) {
				n++;
			}
		}
		return n;
	}

	private static int homesOf(ServerLevel level, int settlement) {
		int n = 0;
		for (String v : sites(level).values()) {
			String[] p = v.split(":");
			for (Kind k : Kind.values()) {
				if (k.home() && k.id.equals(p[0]) && Integer.toString(settlement).equals(p[1])) {
					n++;
				}
			}
		}
		return n;
	}

	// ---- rising --------------------------------------------------------------------------------------------------------

	private static void rise(ServerLevel level) {
		List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator() && Stacked.at(level, p.blockPosition(), Level.OVERWORLD)).toList();
		if (players.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		List<Kind> order = new ArrayList<>(List.of(Kind.values()));
		Collections.shuffle(order, new java.util.Random(random.nextLong()));
		for (int i = 0; i < TRIES && i < order.size(); i++) {
			ServerPlayer player = players.get(random.nextInt(players.size()));
			boolean built = switch (order.get(i)) {
				case FARMHOUSE, SHEPHERD, WOODCUTTER -> home(level, player, random, order.get(i));
				case TRADING_POST -> tradingPost(level, player, random);
				case VILLAGE_TOWER -> villageTower(level, player, random);
				case LOOKOUT -> lookout(level, player, random);
				case EVOKER_TOWER -> evokerTower(level, player, random);
				case EXILE_CABIN -> exileCabin(level, player, random);
			};
			if (built) {
				return;
			}
		}
	}

	/** {@code at} is free for an outlying building: loaded and entity-ticking, no realm's land, nobody watching, nothing of ours close. */
	private static boolean free(ServerLevel level, BlockPos at, Kind kind) {
		MinecraftServer server = level.getServer();
		if (!level.isPositionEntityTicking(at) || Raids.playerNear(level, at, HIDE_RANGE)) {
			return false;
		}
		for (int[] d : new int[][] {{0, 0}, {10, 10}, {-10, 10}, {10, -10}, {-10, -10}}) {
			if (Factions.at(server, level, at.offset(d[0], 0, d[1])) != null) {
				return false;
			}
		}
		return near(level, at, null, APART) == 0 && near(level, at, kind, APART_SAME) == 0;
	}

	/** The ground round {@code center} (within {@code half}) is loaded, dry and no more than {@code spread} high from lowest to highest. */
	private static boolean flat(ServerLevel level, BlockPos center, int half, int spread) {
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int dx = -half; dx <= half; dx += 3) {
			for (int dz = -half; dz <= half; dz += 3) {
				BlockPos column = center.offset(dx, 0, dz);
				if (!Loaded.made(level, column)) {
					return false;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				if (!level.getFluidState(top.below()).isEmpty() || !level.getFluidState(top).isEmpty()) {
					return false;
				}
				int y = VillageStyles.groundY(level, column.getX(), column.getZ());
				lo = Math.min(lo, y);
				hi = Math.max(hi, y);
			}
		}
		return hi - lo <= spread;
	}

	/** Standing trees (leaves or logs on top of the ground) among 16 points eight blocks round {@code at}. */
	private static int trees(ServerLevel level, BlockPos at) {
		int n = 0;
		for (int i = 0; i < 16; i++) {
			double a = Math.PI * 2.0 * i / 16.0;
			BlockPos column = at.offset((int)Math.round(Math.cos(a) * 8), 0, (int)Math.round(Math.sin(a) * 8));
			if (!Loaded.made(level, column)) {
				continue;
			}
			BlockState top = level.getBlockState(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column).below());
			if (top.is(net.minecraft.tags.BlockTags.LEAVES) || top.is(net.minecraft.tags.BlockTags.LOGS)) {
				n++;
			}
		}
		return n;
	}

	/** Stamps {@code bp}, its front middle at {@code at} (on the ground there), its front toward {@code facing}. */
	private static CampBuildings.Placer stamp(ServerLevel level, Blueprint bp, BlockPos at, Direction facing) {
		BlockPos anchor = at.atY(VillageStyles.groundY(level, at.getX(), at.getZ()));
		VillageStyles.stamp(level, bp, anchor, facing, Blocks.COBBLESTONE.defaultBlockState());
		return new CampBuildings.Placer(anchor, facing, bp.width());
	}

	private static @Nullable Faction settlementNear(ServerLevel level, ServerPlayer player, RandomSource random, int reach) {
		List<Faction> near = new ArrayList<>();
		for (Faction f : Factions.all(level.getServer())) {
			if (!f.fallen() && f.type() != FactionType.BARBARIAN && Raids.horizontal(f.seat(), player.blockPosition()) <= (double)reach * reach) {
				near.add(f);
			}
		}
		return near.isEmpty() ? null : near.get(random.nextInt(near.size()));
	}

	/** The nearest standing camp (of the illagers' kind if {@code illagers}, any camp if not) within {@link #TOWER_REACH} of {@code from}, or null. */
	private static @Nullable Faction nearestCamp(MinecraftServer server, BlockPos from) {
		Faction best = null;
		double bestD = (double)TOWER_REACH * TOWER_REACH;
		for (Faction f : Factions.all(server, FactionType.BARBARIAN)) {
			double d = Raids.horizontal(f.seat(), from);
			if (!f.fallen() && d < bestD) {
				bestD = d;
				best = f;
			}
		}
		return best;
	}

	// ---- V15: homes out by the work ------------------------------------------------------------------------------------

	private static boolean home(ServerLevel level, ServerPlayer player, RandomSource random, Kind kind) {
		Faction village = settlementNear(level, player, random, VILLAGE_REACH);
		if (village == null || homesOf(level, village.id()) >= HOMES_PER_VILLAGE) {
			return false;
		}
		double angle = random.nextDouble() * Math.PI * 2.0;
		int r = HOME_MIN + random.nextInt(HOME_MAX - HOME_MIN + 1);
		BlockPos at = village.seat().offset((int)Math.round(Math.cos(angle) * r), 0, (int)Math.round(Math.sin(angle) * r));
		if (!Loaded.made(level, at) || !free(level, at, kind)) {
			return false;
		}
		String biome = VillageStyles.biome(level, at);
		boolean fits = switch (kind) {
			case FARMHOUSE -> VillageStyles.has(biome, "plains", "meadow", "forest", "savanna") && !VillageStyles.has(biome, "snow", "frozen", "dark", "swamp", "river", "ocean");
			case SHEPHERD -> VillageStyles.has(biome, "plains", "meadow", "savanna", "hills", "grove") && !VillageStyles.has(biome, "snow", "frozen", "river", "ocean");
			default -> VillageStyles.has(biome, "forest", "taiga", "grove") && trees(level, at) >= TREES_WOOD;
		};
		Direction facing = Direction.getApproximateNearest(village.seat().getX() - at.getX(), 0, village.seat().getZ() - at.getZ());
		Direction back = facing.getOpposite();
		if (!fits || !flat(level, at.relative(back, 5), 9, 3)) {
			return false;
		}
		Blueprint house = switch (kind) {
			case FARMHOUSE -> OutlyingDesigns.farmhouse();
			case SHEPHERD -> OutlyingDesigns.shepherdHut();
			default -> OutlyingDesigns.woodcutterCabin();
		};
		Blueprint yard = switch (kind) {
			case FARMHOUSE -> OutlyingDesigns.field();
			case SHEPHERD -> OutlyingDesigns.pasture();
			default -> OutlyingDesigns.woodpile();
		};
		CampBuildings.Placer door = stamp(level, house, at, facing);
		// the work beside it, to the house's right
		BlockPos beside = at.relative(facing.getClockWise(), house.width() / 2 + 1 + yard.width() / 2);
		CampBuildings.Placer work = stamp(level, yard, beside, facing);
		BlockPos inside = Construction.standable(level, door.at(house.width() / 2, 1, house.depth() / 2), 3);
		Villager v = EntityTypes.VILLAGER.spawn(level, inside, EntitySpawnReason.STRUCTURE);
		if (v != null) {
			var job = switch (kind) {
				case FARMHOUSE -> VillagerProfession.FARMER;
				case SHEPHERD -> VillagerProfession.SHEPHERD;
				default -> VillagerProfession.FLETCHER;
			};
			v.setVillagerData(v.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(job)).withLevel(2));
			v.setVillagerXp(Math.max(v.getVillagerXp(), VillagerData.getMinXpPerLevel(2)));
			v.setPersistenceRequired();
		}
		if (kind == Kind.SHEPHERD) {
			for (int i = 0; i < 4; i++) {
				EntityTypes.SHEEP.spawn(level, Construction.standable(level, work.at(3 + i * 2 % 7, 1, 4), 2), EntitySpawnReason.STRUCTURE);
			}
		}
		mark(level, at, kind, village.id());
		JaymosKingdoms.LOGGER.info("A {} out by its work rose at {}, a walk from {}: its job needs {}", kind.id, at, village.name(),
			kind == Kind.FARMHOUSE ? "open farmland" : kind == Kind.SHEPHERD ? "grass to graze" : "standing forest");
		return true;
	}

	// ---- V16: a roadside trading post -----------------------------------------------------------------------------------

	private static boolean tradingPost(ServerLevel level, ServerPlayer player, RandomSource random) {
		MinecraftServer server = level.getServer();
		List<Roads.Road> roads = new ArrayList<>(Roads.network(server).roads().stream().filter(r -> r.points().size() >= 2).toList());
		Collections.shuffle(roads, new java.util.Random(random.nextLong()));
		for (Roads.Road road : roads) {
			Roads.Line line = Roads.line(road);
			if (line.length() < LONG_ROAD) {
				continue;
			}
			double half = line.length() / 2.0;
			Vec3 mid = line.at(half), next = line.at(half + 6.0);
			if (Math.hypot(mid.x - player.getX(), mid.z - player.getZ()) > VILLAGE_REACH || !Loaded.made(level, BlockPos.containing(mid))) {
				continue;
			}
			double dx = next.x - mid.x, dz = next.z - mid.z, len = Math.max(1.0, Math.hypot(dx, dz));
			int sign = random.nextBoolean() ? 1 : -1;
			BlockPos at = BlockPos.containing(mid.x - dz / len * POST_BESIDE * sign, mid.y, mid.z + dx / len * POST_BESIDE * sign);
			boolean clear = near(level, at, Kind.TRADING_POST, POST_APART) == 0;
			for (Faction f : Factions.all(server)) {
				clear &= Raids.horizontal(f.seat(), at) >= (double)POST_CLEAR * POST_CLEAR;
			}
			Direction facing = Direction.getApproximateNearest(mid.x - at.getX(), 0, mid.z - at.getZ());
			if (!clear || !free(level, at, Kind.TRADING_POST) || !flat(level, at.relative(facing.getOpposite(), 3), 5, 2)) {
				continue;
			}
			Blueprint post = OutlyingDesigns.tradingPost();
			CampBuildings.Placer placer = stamp(level, post, at, facing);
			var trader = EntityTypes.WANDERING_TRADER.spawn(level, placer.at(OutlyingDesigns.TRADER_SPOT[0], OutlyingDesigns.TRADER_SPOT[1], OutlyingDesigns.TRADER_SPOT[2]),
				EntitySpawnReason.STRUCTURE);
			if (trader != null) {
				trader.setPersistenceRequired();
				trader.setHomeTo(trader.blockPosition(), 5);
			}
			mark(level, at, Kind.TRADING_POST, road.a());
			JaymosKingdoms.LOGGER.info("A roadside trading post rose at {}, halfway along a road of {} blocks: a trader wants where travellers stop", at, (int)line.length());
			return true;
		}
		return false;
	}

	// ---- V17: watchtowers on both sides ---------------------------------------------------------------------------------

	/** The highest ground within {@link #HIGH_LOOK} of {@code around} (a 5 x 5 spread of columns), or null if any is unloaded. */
	private static @Nullable BlockPos high(ServerLevel level, BlockPos around) {
		BlockPos best = null;
		for (int dx = -HIGH_LOOK; dx <= HIGH_LOOK; dx += 6) {
			for (int dz = -HIGH_LOOK; dz <= HIGH_LOOK; dz += 6) {
				BlockPos column = around.offset(dx, 0, dz);
				if (!Loaded.made(level, column)) {
					return null;
				}
				BlockPos here = column.atY(VillageStyles.groundY(level, column.getX(), column.getZ()));
				if (best == null || here.getY() > best.getY()) {
					best = here;
				}
			}
		}
		return best;
	}

	private static boolean villageTower(ServerLevel level, ServerPlayer player, RandomSource random) {
		MinecraftServer server = level.getServer();
		Faction village = settlementNear(level, player, random, VILLAGE_REACH);
		if (village == null) {
			return false;
		}
		Faction foe = nearestCamp(server, village.seat());
		if (foe == null) {
			return false;
		}
		BlockPos tower = towerSite(level, village.seat(), foe.seat(), random, Kind.VILLAGE_TOWER);
		if (tower == null) {
			return false;
		}
		// (one tower to a settlement)
		for (String v : sites(level).values()) {
			if (v.startsWith(Kind.VILLAGE_TOWER.id + ":" + village.id() + ":")) {
				return false;
			}
		}
		Direction facing = Direction.getApproximateNearest(village.seat().getX() - tower.getX(), 0, village.seat().getZ() - tower.getZ());
		CampBuildings.Placer placer = stamp(level, OutlyingDesigns.watchtower(false), tower, facing);
		BlockPos fire = placer.at(OutlyingDesigns.TOWER_FIRE[0], OutlyingDesigns.TOWER_FIRE[1], OutlyingDesigns.TOWER_FIRE[2]);
		BlockPos guard = placer.at(OutlyingDesigns.TOWER_GUARD[0], OutlyingDesigns.TOWER_GUARD[1], OutlyingDesigns.TOWER_GUARD[2]);
		Folk archer = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.STRUCTURE);
		if (archer != null) {
			archer.snapTo(guard.getX() + 0.5, guard.getY(), guard.getZ() + 0.5, facing.getOpposite().toYRot(), 0.0F);
			archer.setup(Folk.ARCHER, village, guard, 1);
			level.addFreshEntity(archer);
		}
		mark(level, fire, Kind.VILLAGE_TOWER, village.id());
		JaymosKingdoms.LOGGER.info("A watchtower rose at {} on the high ground between {} and {}: it watches the way the raiders come", fire, village.name(), foe.name());
		return true;
	}

	private static boolean lookout(ServerLevel level, ServerPlayer player, RandomSource random) {
		MinecraftServer server = level.getServer();
		List<Faction> camps = Factions.all(server, FactionType.BARBARIAN).stream().filter(c -> !c.fallen() && IllagerCamps.is(server, c.id())
			&& Raids.horizontal(c.seat(), player.blockPosition()) <= (double)VILLAGE_REACH * VILLAGE_REACH).toList();
		if (camps.isEmpty()) {
			return false;
		}
		Faction camp = camps.get(random.nextInt(camps.size()));
		for (String v : sites(level).values()) {
			if (v.startsWith(Kind.LOOKOUT.id + ":" + camp.id() + ":")) {
				return false;
			}
		}
		Faction prey = null;
		double bestD = (double)TOWER_REACH * TOWER_REACH;
		for (Faction f : Factions.all(server)) {
			double d = Raids.horizontal(f.seat(), camp.seat());
			if (!f.fallen() && f.type() != FactionType.BARBARIAN && d < bestD) {
				bestD = d;
				prey = f;
			}
		}
		if (prey == null) {
			return false;
		}
		BlockPos tower = towerSite(level, camp.seat(), prey.seat(), random, Kind.LOOKOUT);
		if (tower == null) {
			return false;
		}
		Direction facing = Direction.getApproximateNearest(camp.seat().getX() - tower.getX(), 0, camp.seat().getZ() - tower.getZ());
		CampBuildings.Placer placer = stamp(level, OutlyingDesigns.watchtower(true), tower, facing);
		BlockPos fire = placer.at(OutlyingDesigns.TOWER_FIRE[0], OutlyingDesigns.TOWER_FIRE[1], OutlyingDesigns.TOWER_FIRE[2]);
		BlockPos guard = placer.at(OutlyingDesigns.TOWER_GUARD[0], OutlyingDesigns.TOWER_GUARD[1], OutlyingDesigns.TOWER_GUARD[2]);
		BlockPos banner = placer.at(OutlyingDesigns.TOWER_BANNER[0], OutlyingDesigns.TOWER_BANNER[1], OutlyingDesigns.TOWER_BANNER[2]);
		IllagerBuildings.ominous(level, banner);
		Illagers.spawn(level, camp, Illagers.PILLAGER, guard, guard, 1, false);
		mark(level, fire, Kind.LOOKOUT, camp.id());
		JaymosKingdoms.LOGGER.info("An illager lookout rose at {} on the high ground from {} toward {}: it watches the way its prey comes", fire, camp.name(), prey.name());
		return true;
	}

	/** The highest ground {@link #TOWER_OUT} blocks and more out from {@code from} toward {@code toward}, free and level enough for a tower; or null. */
	private static @Nullable BlockPos towerSite(ServerLevel level, BlockPos from, BlockPos toward, RandomSource random, Kind kind) {
		double dx = toward.getX() - from.getX(), dz = toward.getZ() - from.getZ(), len = Math.max(1.0, Math.hypot(dx, dz));
		int r = TOWER_OUT + random.nextInt(TOWER_SPREAD + 1);
		BlockPos around = from.offset((int)Math.round(dx / len * r), 0, (int)Math.round(dz / len * r));
		BlockPos top = high(level, around);
		if (top == null || !free(level, top, kind) || !flat(level, top, 3, 4)) {
			return null;
		}
		return top;
	}

	/** Every {@link #WATCH_TICKS}: each tower with a player in reach keeps its watch. */
	private static void watch(ServerLevel level, long now) {
		for (var e : sites(level).entrySet()) {
			String[] p = e.getValue().split(":");
			boolean village = p[0].equals(Kind.VILLAGE_TOWER.id), lookout = p[0].equals(Kind.LOOKOUT.id);
			if (!village && !lookout) {
				continue;
			}
			BlockPos fire = keyPos(e.getKey(), Integer.parseInt(p[2]));
			if (!Loaded.made(level, fire) || !Raids.playerNear(level, fire, WATCH_RANGE)) {
				continue;
			}
			if (village) {
				watchVillage(level, fire, now);
			} else {
				watchLookout(level, fire, now);
			}
		}
	}

	/** A village tower: a raider near lights the signal fire and calls the village's guards; a quiet while puts it out. */
	private static void watchVillage(ServerLevel level, BlockPos fire, long now) {
		long id = fire.asLong();
		Mob threat = null;
		for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(fire).inflate(WARN_RANGE), m -> m.isAlive() && raider(m))) {
			threat = m;
			break;
		}
		BlockState state = level.getBlockState(fire);
		if (threat != null) {
			WANTED.put(id, now);
			if (state.is(Blocks.CAMPFIRE) && !state.getValue(CampfireBlock.LIT)) {
				level.setBlock(fire, state.setValue(CampfireBlock.LIT, true), Block.UPDATE_ALL);
				level.playSound(null, fire, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, 0.6F);
			}
			if (now - ALERTED.getOrDefault(id, -ALERT_GAP) >= ALERT_GAP) {
				ALERTED.put(id, now);
				AlarmBells.answer(level, fire, AlarmBells.CALL_REACH, threat, m -> true);
			}
		} else if (state.is(Blocks.CAMPFIRE) && state.getValue(CampfireBlock.LIT) && now - WANTED.getOrDefault(id, 0L) >= DOUSE_TICKS) {
			level.setBlock(fire, state.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL);
		}
	}

	/** One of the greenskins' or illagers' or the camps' raiding folk: what a village's tower warns of. */
	private static boolean raider(Mob m) {
		return m instanceof AbstractIllager || Orcs.isOrc(m) || Raids.isRaider(m) || m instanceof Folk f && f.style() == FactionType.BARBARIAN;
	}

	/** A lookout: his sentry sees a player first, cries out, and the camp's illagers turn on them. */
	private static void watchLookout(ServerLevel level, BlockPos fire, long now) {
		long id = fire.asLong();
		if (now - ALERTED.getOrDefault(id, -LOOKOUT_GAP) < LOOKOUT_GAP) {
			return;
		}
		AABB around = new AABB(fire).inflate(3.0);
		Mob sentry = null;
		for (Mob m : level.getEntitiesOfClass(Mob.class, around, m -> m.isAlive() && m instanceof AbstractIllager)) {
			sentry = m;
			break;
		}
		if (sentry == null) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator() || player.distanceToSqr(sentry) > SPOT_RANGE * SPOT_RANGE || !sentry.hasLineOfSight(player)) {
				continue;
			}
			ALERTED.put(id, now);
			level.playSound(null, fire, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0F, 0.5F);
			for (Mob illager : level.getEntitiesOfClass(Mob.class, new AABB(fire).inflate(CALL_RANGE), m -> m.isAlive() && m instanceof AbstractIllager && m.getTarget() == null)) {
				illager.setTarget(player);
			}
			return;
		}
	}

	// ---- V19 and V20: the hidden ones ----------------------------------------------------------------------------------

	private static boolean evokerTower(ServerLevel level, ServerPlayer player, RandomSource random) {
		BlockPos at = VillageStyles.ring(player, random);
		if (!Loaded.made(level, at) || !VillageStyles.has(VillageStyles.biome(level, at), "snowy_taiga") || random.nextFloat() >= EVOKER_CHANCE
			|| near(level, at, Kind.EVOKER_TOWER, EVOKER_APART) > 0 || !free(level, at, Kind.EVOKER_TOWER)) {
			return false;
		}
		Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		if (!flat(level, at.relative(facing.getOpposite(), 3), 5, 3)) {
			return false;
		}
		CampBuildings.Placer placer = stamp(level, OutlyingDesigns.frozenTower(), at, facing);
		if (level.getBlockEntity(placer.at(OutlyingDesigns.LAB_CHEST[0], OutlyingDesigns.LAB_CHEST[1], OutlyingDesigns.LAB_CHEST[2])) instanceof ChestBlockEntity chest) {
			chest.setLootTable(IllagerBuildings.LAB_LOOT, random.nextLong());
			chest.setChanged();
		}
		BlockPos spot = placer.at(OutlyingDesigns.EVOKER_SPOT[0], OutlyingDesigns.EVOKER_SPOT[1], OutlyingDesigns.EVOKER_SPOT[2]);
		var evoker = EntityTypes.EVOKER.spawn(level, spot, EntitySpawnReason.STRUCTURE);
		if (evoker != null) {
			evoker.setPersistenceRequired();
			evoker.setHomeTo(spot, 6);
		}
		mark(level, at, Kind.EVOKER_TOWER, -1);
		JaymosKingdoms.LOGGER.info("The evoker's frozen tower rose at {} in snowy taiga: he hides his studies from his own kind where no raider goes for the cold", at);
		return true;
	}

	private static boolean exileCabin(ServerLevel level, ServerPlayer player, RandomSource random) {
		BlockPos at = VillageStyles.ring(player, random);
		if (!Loaded.made(level, at) || !VillageStyles.has(VillageStyles.biome(level, at), "dark_forest") || !free(level, at, Kind.EXILE_CABIN)
			|| near(level, at, Kind.EXILE_CABIN, EXILE_APART) > 0 || trees(level, at) < TREES_DEEP) {
			return false;
		}
		for (Faction f : Factions.all(level.getServer())) {
			if (Raids.horizontal(f.seat(), at) < EXILE_CLEAR * EXILE_CLEAR) {
				return false;
			}
		}
		Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		if (!flat(level, at.relative(facing.getOpposite(), 3), 7, 3)) {
			return false;
		}
		Blueprint cabin = OutlyingDesigns.exileCabin();
		CampBuildings.Placer door = stamp(level, cabin, at, facing);
		stamp(level, OutlyingDesigns.woodpile(), at.relative(facing.getClockWise(), cabin.width() / 2 + 5), facing);
		BlockPos inside = Construction.standable(level, door.at(cabin.width() / 2, 1, cabin.depth() / 2), 3);
		var axeman = EntityTypes.VINDICATOR.spawn(level, inside, EntitySpawnReason.STRUCTURE);
		if (axeman != null) {
			axeman.setPersistenceRequired();
			axeman.setHomeTo(inside, 10);
		}
		mark(level, at, Kind.EXILE_CABIN, -1);
		JaymosKingdoms.LOGGER.info("An exile's cabin rose at {} in dark forest, {} blocks from any settlement: one vindicator driven out of his camp", at, (int)EXILE_CLEAR);
		return true;
	}
}
