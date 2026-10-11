package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.5) Where a settlement's people live. Its working folk (builders, farmer, miners, woodcutters) go home at
 * nightfall and sleep in a bed, in one of its houses or barracks first, else any bed about the seat no villager has
 * claimed, and are back at work at dawn; guards and archers keep the night watch. A player who wants a bed one of them
 * is sleeping in gets them out of it. And every house its builders finish brings a villager to live in it, while the
 * settlement has more beds than villagers.
 */
final class Homes {
	/** Bedtime and getting up, by the time of day (vanilla villagers turn in at about 12000). */
	static final long BEDTIME = 12500L, WAKE = 23500L;
	/** How far from the seat its folk look for a bed. */
	static final int BED_RANGE = 80;
	/** Their pace home. */
	static final double WALK_SPEED = 0.6;
	/** How near a bed they lie down. */
	static final double BED_REACH = 1.5;

	/** Each sleeper's bed (the head), while it has one. */
	private static final Map<UUID, BlockPos> BEDS = new HashMap<>();
	private static final Map<UUID, Gatherers.Trip> TRIPS = new HashMap<>();

	private Homes() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world instanceof ServerLevel level) {
				wanted(level, hit.getBlockPos());
			}
			return InteractionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BEDS.clear();
			TRIPS.clear();
		});
	}

	/** Its role goes to bed at night (the working folk; the guard keeps watch, rulers keep their own hours). */
	static boolean sleeper(int role) {
		return Folk.isBuilder(role) || role == Folk.FARMER || role == Folk.MINER || role == Folk.WOODCUTTER;
	}

	static boolean bedtime(ServerLevel level) {
		long t = Royals.timeOfDay(level);
		return t >= BEDTIME && t < WAKE;
	}

	/**
	 * Its night (from {@link Folk#customServerAiStep}): true while it's bedtime and this sleeper is on its way to bed or
	 * home (its work waits); false by day, or for one who doesn't sleep.
	 */
	static boolean tick(ServerLevel level, Folk folk) {
		if (!sleeper(folk.role()) || !bedtime(level)) {
			TRIPS.remove(folk.getUUID());
			return false;
		}
		Faction f = Factions.get(level.getServer(), folk.factionId());
		if (f == null || f.fallen() || folk.getTarget() != null) {
			return false;
		}
		if (folk.isSleeping()) {
			return true;
		}
		long now = level.getGameTime();
		Gatherers.Trip trip = TRIPS.computeIfAbsent(folk.getUUID(), id -> {
			Gatherers.Trip t = new Gatherers.Trip();
			t.reset(now);
			return t;
		});
		BlockPos bed = bed(level, f, folk);
		if (bed == null) {
			// (no bed for it: home, and it stays there till morning)
			folk.setTask("home for the night");
			Gatherers.walk(folk, trip, folk.getHomePosition(), 2.0, WALK_SPEED, now);
			return true;
		}
		folk.setTask("going to bed");
		if (Gatherers.walk(folk, trip, bed, BED_REACH, WALK_SPEED, now)) {
			if (!level.getBlockState(bed).getValue(BedBlock.OCCUPIED) && folk.startSleeping(bed)) {
				folk.setTask("asleep");
			} else {
				// (someone's in it: another)
				BEDS.remove(folk.getUUID());
			}
		}
		return true;
	}

	/** Asleep (from {@link Folk#aiStep}): up at dawn, or when a fight starts. */
	static void asleep(ServerLevel level, Folk folk) {
		if (!bedtime(level) || folk.getTarget() != null || folk.getLastHurtByMob() != null && folk.tickCount - folk.getLastHurtByMobTimestamp() < 100) {
			folk.stopSleeping();
			folk.setTask("");
			TRIPS.remove(folk.getUUID());
		}
	}

	/** Its bed (the head): the one it has if still free to it, else the nearest free one; null if none. */
	private static @Nullable BlockPos bed(ServerLevel level, Faction f, Folk folk) {
		UUID id = folk.getUUID();
		BlockPos had = BEDS.get(id);
		if (had != null && free(level, had)) {
			return had;
		}
		BEDS.remove(id);
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		// its own houses' and barracks' beds count as nearer than the rest
		for (Works.Built b : Works.of(level.getServer(), f.id()).built()) {
			if (!b.entry().equals("house") && !b.entry().equals("barracks")) {
				continue;
			}
			for (BlockPos p : BlockPos.betweenClosed(b.min(), b.max())) {
				if (head(level.getBlockState(p)) && free(level, p) && !taken(id, p)) {
					double d = p.distSqr(folk.blockPosition()) - 1_000_000;
					if (d < bestD) {
						bestD = d;
						best = p.immutable();
					}
				}
			}
		}
		if (best == null) {
			// any bed about the seat that no villager has claimed (a bed is a home to them)
			PoiManager pois = level.getPoiManager();
			Optional<BlockPos> any = pois.findClosest(type -> type.is(PoiTypes.HOME), p -> free(level, p) && !taken(id, p),
				folk.blockPosition(), BED_RANGE, PoiManager.Occupancy.HAS_SPACE);
			if (any.isPresent() && any.get().closerThan(f.seat(), BED_RANGE)) {
				best = any.get().immutable();
			}
		}
		if (best != null) {
			BEDS.put(id, best);
		}
		return best;
	}

	private static boolean head(BlockState state) {
		return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
	}

	private static boolean free(ServerLevel level, BlockPos head) {
		BlockState state = level.getBlockState(head);
		return head(state) && !state.getValue(BedBlock.OCCUPIED);
	}

	/** Some other sleeper's. */
	private static boolean taken(UUID self, BlockPos bed) {
		for (Map.Entry<UUID, BlockPos> e : BEDS.entrySet()) {
			if (!e.getKey().equals(self) && e.getValue().equals(bed)) {
				return true;
			}
		}
		return false;
	}

	/** A player is using a bed: any of the folk asleep in it gets up (and finds another). */
	private static void wanted(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof BedBlock)) {
			return;
		}
		BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD ? pos : pos.relative(state.getValue(BedBlock.FACING));
		for (Folk folk : level.getEntitiesOfClass(Folk.class, new AABB(head).inflate(2.0), Folk::isSleeping)) {
			if (folk.getSleepingPos().filter(head::equals).isPresent()) {
				folk.stopSleeping();
				BEDS.remove(folk.getUUID());
			}
		}
	}

	// ---- newcomers --------------------------------------------------------------------------------------------------

	/** A building finished ({@link Construction}): a new house brings a villager to live in it, while beds go spare. */
	static void built(ServerLevel level, Faction f, String entry, BlockPos min, BlockPos max) {
		if (!entry.equals("house")) {
			return;
		}
		AABB around = new AABB(f.seat()).inflate(Economy.POPULATION_RANGE);
		int villagers = level.getEntitiesOfClass(Villager.class, around, Villager::isAlive).size();
		long beds = level.getPoiManager().getInRange(type -> type.is(PoiTypes.HOME), f.seat(), (int)Economy.POPULATION_RANGE, PoiManager.Occupancy.ANY).count();
		if (villagers >= beds) {
			return;
		}
		BlockPos mid = new BlockPos((min.getX() + max.getX()) / 2, min.getY() + 1, (min.getZ() + max.getZ()) / 2);
		BlockPos at = Construction.standable(level, mid, 3);
		Villager v = EntityTypes.VILLAGER.spawn(level, at, EntitySpawnReason.STRUCTURE);
		if (v != null) {
			JaymosKingdoms.LOGGER.debug("a villager moved into {}'s new house at {}", f.name(), at);
		}
	}
}
