package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.83) Villagers shut in. Andy found a settlement's townsfolk packed into a closed pocket of a building they had
 * no way out of (they'd fled into it, or been put there), and there they'd have stayed: {@link Unstick} only sees a mob
 * with a path, and a villager with nowhere to go has none. Now a villager near a settlement that hasn't got anywhere
 * for {@link #STILL_TICKS} of the day, and can't find a way to open ground near it, turns up there in a puff, as our
 * own folk do. Not where a player built round them (a trading hall is theirs to keep), nor asleep, tied up or carried.
 */
public final class ShutIn {
	/** How often it's looked for (ticks); how near a seat villagers are watched. */
	static final int EVERY = 100;
	static final double WATCHED = 96.0;
	/** Moving less than this far across, for this long by day, and it's looked into. */
	static final double STILL = 3.0;
	static final long STILL_TICKS = 2400L;
	/** Open ground is sought this far round it (blocks), and no higher or lower than this from where it stands. */
	static final int SEEK = 14, RISE = 3, DROP = 6;
	/** A block a player placed within this far of it: it's their doing, and left be. */
	static final int PLAYER_BUILT = 3;

	/** Each watched villager: where it was, and since when it has been about there. */
	private record Still(Vec3 at, long since) {
	}

	private static final Map<UUID, Still> STILLS = new HashMap<>();

	private ShutIn() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ShutIn::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STILLS.clear());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		if (Homes.bedtime(level)) {
			// (at night they're meant to be in)
			STILLS.clear();
			return;
		}
		Map<UUID, Still> seen = new HashMap<>();
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(WATCHED), Villager::isAlive)) {
				if (seen.containsKey(v.getUUID())) {
					continue;
				}
				if (v.isSleeping() || v.isPassenger() || v.isNoAi() || v.isLeashed() || Downed.isDowned(v) || Hermits.is(v)) {
					continue;
				}
				Still was = STILLS.get(v.getUUID());
				if (was == null || horizontal(was.at(), v.position()) > STILL) {
					seen.put(v.getUUID(), new Still(v.position(), now));
					continue;
				}
				if (now - was.since() >= STILL_TICKS && free(level, v)) {
					seen.put(v.getUUID(), new Still(v.position(), now));
					continue;
				}
				seen.put(v.getUUID(), was);
			}
		}
		STILLS.clear();
		STILLS.putAll(seen);
	}

	/** Whether it could walk out to open ground; if not (and no player shut it in), it's put there. True if it was looked into. */
	private static boolean free(ServerLevel level, Villager v) {
		if (playerBuilt(level, v.blockPosition())) {
			return true;
		}
		BlockPos open = openGround(level, v.blockPosition());
		if (open == null) {
			return true;
		}
		Path path = v.getNavigation().createPath(open, 1);
		if (path != null && path.canReach()) {
			return true;
		}
		level.sendParticles(ParticleTypes.POOF, v.getX(), v.getY() + 1.0, v.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		v.getNavigation().stop();
		v.teleportTo(open.getX() + 0.5, open.getY(), open.getZ() + 0.5);
		level.sendParticles(ParticleTypes.POOF, open.getX() + 0.5, open.getY() + 1.0, open.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
		JaymosKingdoms.LOGGER.debug("{} was shut in at {}: out to {}", v.getName().getString(), v.blockPosition(), open);
		return true;
	}

	/**
	 * The nearest spot under the open sky about {@code near}'s height (not a roof): solid underfoot, room for a villager,
	 * no water. Null if none is near.
	 */
	public static @Nullable BlockPos openGround(ServerLevel level, BlockPos near) {
		for (int r = 2; r <= SEEK; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = near.getX() + dx, z = near.getZ() + dz;
					BlockPos at = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
					if (at.getY() > near.getY() + RISE || at.getY() < near.getY() - DROP) {
						continue;
					}
					if (level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
						&& !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty() && level.getFluidState(at).isEmpty()
						&& level.getFluidState(at.below()).isEmpty()) {
						return at;
					}
				}
			}
		}
		return null;
	}

	private static boolean playerBuilt(ServerLevel level, BlockPos at) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-PLAYER_BUILT, -1, -PLAYER_BUILT), at.offset(PLAYER_BUILT, 2, PLAYER_BUILT))) {
			if (Placed.any(level, p)) {
				return true;
			}
		}
		return false;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
