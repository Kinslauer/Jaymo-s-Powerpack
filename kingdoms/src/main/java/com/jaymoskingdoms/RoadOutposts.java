package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Rebuilt illager outposts (V14, {@link VillageStyles}): a pillager outpost with a log palisade, a cage and a watchtower,
 * which is what an {@link IllagerCamps illager camp} is, raised on the road into a realm the illagers are raiding.
 * <p>
 * Where: beside one of the King's Roads ({@link Roads}) that runs into a realm which already has an illager camp within
 * {@link Camps#CAMP_AREA} of its seat, {@link #FROM_MIN} to {@link #FROM_MAX} blocks out from the realm's seat, beside the road
 * ({@link #BESIDE} blocks off it), the camp's gate toward the road, with a toll bar across the road. Why: a raiding band
 * wants the way its prey travels: the road is where the caravans and the king's riders go, and a camp on it can stop
 * both. One outpost to a realm.
 * <p>
 * The camp is an ordinary illager camp in every way (its people, its growth, its raids, its fall with its warlord); the
 * outpost's own parts are the palisade of logs in place of the fence ring, and the toll bar.
 */
final class RoadOutposts {
	/** How often (ticks) one may rise near a player, and the chance each time. */
	static final int RISE_TICKS = 20 * 120;
	static final float RISE_CHANCE = 0.25F;
	/** Out from the realm's seat along the road (blocks), and off the road's line. */
	static final int FROM_MIN = 170, FROM_MAX = 290, BESIDE = 26;
	/** The most outposts to a realm. */
	static final int PER_REALM = 1;
	/** A realm's roads are looked at when a player is within this of its seat. */
	static final double PLAYER_REACH = 420.0;
	/** The palisade's height, and the toll bar's half-width. */
	static final int PALISADE_HIGH = 3, BAR_HALF = 3;
	/** The style id kept for an outpost's camp. */
	static final VillageStyles.Style STYLE = new VillageStyles.Style("road_outpost",
		"on a road into a realm that illagers raid: a raiding band wants the way its prey travels");

	private RoadOutposts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			if (level.getGameTime() % RISE_TICKS == 0 && level.getDifficulty() != Difficulty.PEACEFUL && level.getRandom().nextFloat() < RISE_CHANCE) {
				rise(level);
			}
		});
	}

	private static void rise(ServerLevel level) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		for (Faction realm : Factions.all(server)) {
			if (!realm.type().isRealm() || realm.fallen() || !Raids.playerNear(level, realm.seat(), PLAYER_REACH) || !raided(server, realm) || outposts(server, realm) >= PER_REALM) {
				continue;
			}
			List<Roads.Road> roads = new ArrayList<>();
			for (Roads.Road road : Roads.network(server).roads()) {
				if ((road.a() == realm.id() || road.b() == realm.id()) && road.points().size() >= 2) {
					roads.add(road);
				}
			}
			if (roads.isEmpty()) {
				continue;
			}
			Roads.Road road = roads.get(random.nextInt(roads.size()));
			Roads.Line line = Roads.line(road);
			boolean fromStart = road.a() == realm.id();
			for (int tries = 0; tries < 4; tries++) {
				double along = FROM_MIN + random.nextInt(FROM_MAX - FROM_MIN + 1);
				if (along >= line.length() - 40) {
					continue;
				}
				double s = fromStart ? along : line.length() - along, t = fromStart ? s + 6 : s - 6;
				Vec3 here = line.at(s), next = line.at(t);
				double dx = next.x - here.x, dz = next.z - here.z, len = Math.max(1.0, Math.hypot(dx, dz));
				int sign = random.nextBoolean() ? 1 : -1;
				BlockPos at = BlockPos.containing(here.x - dz / len * BESIDE * sign, here.y, here.z + dx / len * BESIDE * sign);
				if (!Camps.open(server, level, at)) {
					continue;
				}
				int floor = IllagerBuildings.floor(level, at, IllagerCamps.MAX_SPREAD);
				if (floor == Integer.MIN_VALUE) {
					continue;
				}
				BlockPos road0 = BlockPos.containing(here.x, here.y, here.z);
				Direction gate = Direction.getApproximateNearest(road0.getX() - at.getX(), 0, road0.getZ() - at.getZ());
				Faction camp = IllagerCamps.found(level, at, floor, gate);
				palisade(level, camp.seat(), gate);
				tollBar(level, road0, Direction.getApproximateNearest(dx, 0, dz));
				VillageStyles.remember(server, camp.id(), STYLE);
				JaymosKingdoms.LOGGER.info("An illager outpost, {}, rose at {} on the road into {}: {}", camp.name(), camp.seat(), realm.name(), STYLE.where);
				return;
			}
		}
	}

	/** {@code realm} has an illager camp standing within {@link Camps#CAMP_AREA} of its seat: illagers raid it. */
	private static boolean raided(MinecraftServer server, Faction realm) {
		for (Faction f : Factions.all(server, FactionType.BARBARIAN)) {
			if (!f.fallen() && IllagerCamps.is(server, f.id()) && Raids.horizontal(f.seat(), realm.seat()) <= Camps.CAMP_AREA * Camps.CAMP_AREA) {
				return true;
			}
		}
		return false;
	}

	/** Outposts of this kind standing within {@link Camps#CAMP_AREA} of {@code realm}'s seat. */
	private static int outposts(MinecraftServer server, Faction realm) {
		int n = 0;
		for (Faction f : Factions.all(server, FactionType.BARBARIAN)) {
			if (!f.fallen() && STYLE.id.equals(VillageStyles.styleOf(server, f)) && Raids.horizontal(f.seat(), realm.seat()) <= Camps.CAMP_AREA * Camps.CAMP_AREA) {
				n++;
			}
		}
		return n;
	}

	/** The camp's fence ring made a palisade: dark oak logs {@link #PALISADE_HIGH} high all round, but for the gate (its posts stay). */
	private static void palisade(ServerLevel level, BlockPos fire, Direction gate) {
		CampBuildings.Placer camp = new CampBuildings.Placer(fire, gate, 0);
		int r = IllagerBuildings.RING_RADIUS;
		BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
		for (int x = -r - 1; x <= r + 1; x++) {
			for (int z = -r - 1; z <= r + 1; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d < r - 0.5 || d >= r + 0.5 || (z < 0 && Math.abs(x) <= IllagerBuildings.GATE_HALF + 1)) {
					continue;
				}
				for (int y = 0; y < PALISADE_HIGH; y++) {
					BlockPos at = camp.at(x, y, z);
					level.setBlock(at, y == PALISADE_HIGH - 1 && (x + z) % 2 == 0 ? Blocks.DARK_OAK_FENCE.defaultBlockState() : log, Block.UPDATE_ALL);
				}
			}
		}
	}

	/** A toll bar across the road at {@code at} (the road running toward {@code along}): fence either side of a gate, a lantern on a post. */
	private static void tollBar(ServerLevel level, BlockPos at, Direction along) {
		Direction across = along.getClockWise();
		for (int i = -BAR_HALF; i <= BAR_HALF; i++) {
			BlockPos column = at.relative(across, i);
			BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (i == 0) {
				level.setBlock(top, Blocks.DARK_OAK_FENCE_GATE.defaultBlockState().setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, along), Block.UPDATE_ALL);
			} else {
				level.setBlock(top, Blocks.DARK_OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
				level.setBlock(top.above(), Blocks.DARK_OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
				if (Math.abs(i) == 1) {
					level.setBlock(top.above(2), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
	}
}
