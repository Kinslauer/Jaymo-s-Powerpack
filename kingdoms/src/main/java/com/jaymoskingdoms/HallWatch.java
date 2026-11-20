package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-20, a clip of an empty throne room): "why is the throneroom empty?"
 * <p>
 * A realm's guards are posted two to its throne room and three at its gate ({@code KingdomEvents.guardPost}). The folk
 * guards were held to those posts by their home; now a realm's guards are MCA villagers, and MCA's brain
 * pays no heed to a home, so its hall and gate guards wandered off with MCA's town patrol and nothing ever brought them
 * back: the throne room had nobody at all once the ruler left it. Now a guard posted in the hall or at the gate is held
 * there while on duty ({@link #step}): strayed off, they walk back; at the post, they stand their watch.
 * <p>
 * And the night watch: those posted guards keep it from dusk to dawn ({@link #nightWatch}), so the throne room and the
 * gate are never left empty at night. Their hours among their people (their shift off by day, and the evening) stay
 * as they were; the town's other guards still have the night off.
 */
final class HallWatch {
	/** How far from their post a hall guard, and a gate guard, may be before they walk back to it (blocks). */
	static final double HALL_LEASH = 2.5, GATE_LEASH = 3.5;
	/** A guard is the hall's when their home is this near its middle, and with at most this home radius; the gate's likewise. */
	static final double HALL_NEAR = 6.0, GATE_NEAR = 4.0;
	static final int HALL_RADIUS = 5, GATE_RADIUS = 8;
	/** The night watch, by the time of day: from dusk until dawn. */
	static final long WATCH_FROM = 12000L, WATCH_TO = Royals.DAWN;
	/** How fast they go back to their post. */
	static final double RETURN_SPEED = 0.7;
	/** At their post, they watch a player this near. */
	static final double LOOK_RANGE = 8.0;

	private HallWatch() {
	}

	/** The post {@code guard} keeps in their realm's keep (its hall's or its gate's), or null if they're a town guard. */
	static @Nullable BlockPos post(ServerLevel level, PathfinderMob guard, Worker w) {
		if (w.role() != Folk.GUARD || w.factionId() < 0 || !guard.hasHome()) {
			return null;
		}
		Castle.Keep keep = Factions.keep(level.getServer(), w.factionId());
		if (keep == null) {
			return null;
		}
		BlockPos home = guard.getHomePosition();
		int radius = guard.getHomeRadius();
		if (radius <= HALL_RADIUS && home.closerThan(keep.hall(), HALL_NEAR) || radius <= GATE_RADIUS && home.closerThan(keep.gate(), GATE_NEAR)) {
			return home;
		}
		return null;
	}

	/** {@code guard} keeps the night watch at {@code t}: posted in the hall or at the gate, and it's night. */
	static boolean nightWatch(PathfinderMob guard, Worker w, long t) {
		return (t >= WATCH_FROM || t < WATCH_TO) && guard.level() instanceof ServerLevel level && post(level, guard, w) != null;
	}

	/**
	 * A villager guard's step ({@code Workers.step}, when nothing of their ruler's has them): true while they keep their
	 * post in the hall or at the gate (in place of their brain), false to live as their brain and their day would have it.
	 */
	static boolean step(ServerLevel level, Enlisted w) {
		PathfinderMob guard = w.mob();
		if (guard instanceof Folk || guard.isPassenger() || McaCompat.busy(guard) || Downed.isDowned(guard) || Arrests.busy(guard)
			|| Rulers.target(guard) != null || guard.tickCount % 10 == 0 && Rulers.foeNear(level, guard)) {
			return false;
		}
		BlockPos post = post(level, guard, w);
		long t = Royals.timeOfDay(level);
		if (post == null || Rulers.offDuty(guard, w, t)) {
			return false;
		}
		if (guard.isSleeping()) {
			if (!nightWatch(guard, w, t)) {
				// (on duty by the clock, but abed before dawn: MCA wakes them)
				return false;
			}
			// their turn on the night watch: up
			guard.stopSleeping();
		}
		boolean hall = guard.getHomeRadius() <= HALL_RADIUS && Factions.keep(level.getServer(), w.factionId()) instanceof Castle.Keep keep
			&& post.closerThan(keep.hall(), HALL_NEAR);
		double leash = hall ? HALL_LEASH : GATE_LEASH;
		Vec3 at = Vec3.atBottomCenterOf(post);
		if (guard.position().distanceToSqr(at) > leash * leash) {
			if (guard.getNavigation().isDone() || guard.tickCount % 20 == 0) {
				guard.getNavigation().moveTo(at.x, at.y, at.z, RETURN_SPEED);
			}
			return true;
		}
		guard.getNavigation().stop();
		Player near = level.getNearestPlayer(guard, LOOK_RANGE);
		if (near != null && !near.isSpectator()) {
			guard.getLookControl().setLookAt(near, 30.0F, 30.0F);
		}
		return true;
	}
}
