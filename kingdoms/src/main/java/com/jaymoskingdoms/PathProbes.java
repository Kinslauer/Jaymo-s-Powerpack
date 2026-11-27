package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.PathNavigationAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;

/**
 * The fighting AI's trial paths ({@link Detours}, {@link Hiding}, {@link DangerSense}): worked out to see whether a way goes
 * somewhere, not walked yet. All of them together get {@link #PER_TICK} a tick, server-wide. A trial leaves the navigation
 * as it was: {@code createPath} would point its recomputed path at the trial spot and reset its stuck timer.
 */
public final class PathProbes {
	/** Trial paths a tick, for all mobs together. */
	static final int PER_TICK = 12;

	private static int used;

	private PathProbes() {
	}

	static void register() {
		ServerTickEvents.START_SERVER_TICK.register(server -> used = 0);
	}

	/** Whether {@code n} more trial paths fit in this tick's budget. */
	static boolean room(int n) {
		return used + n <= PER_TICK;
	}

	/** Spends one trial path from this tick's budget: false if it's spent. */
	static boolean take() {
		if (used >= PER_TICK) {
			return false;
		}
		used++;
		return true;
	}

	/** A trial path to {@code to}, the navigation's own target and stuck timer left as they were. */
	static @Nullable Path to(Mob m, BlockPos to, int reach) {
		PathNavigation nav = m.getNavigation();
		Kept kept = new Kept((PathNavigationAccessor)nav);
		try {
			return nav.createPath(to, reach);
		} finally {
			kept.restore();
		}
	}

	/** A trial path to {@code to} (as {@code createPath(Entity, int)}), the navigation left as it was. */
	static @Nullable Path to(Mob m, Entity to, int reach) {
		PathNavigation nav = m.getNavigation();
		Kept kept = new Kept((PathNavigationAccessor)nav);
		try {
			return nav.createPath(to, reach);
		} finally {
			kept.restore();
		}
	}

	/** Walks a trial path after all: its spot becomes the navigation's target (as {@code createPath} would have made it). */
	static boolean follow(Mob m, Path path, int reach, double speed) {
		PathNavigation nav = m.getNavigation();
		if (path.getTarget() != null) {
			PathNavigationAccessor acc = (PathNavigationAccessor)nav;
			acc.jaymoskingdoms$setTargetPos(path.getTarget());
			acc.jaymoskingdoms$setReachRange(reach);
		}
		return nav.moveTo(path, speed);
	}

	private static final class Kept {
		private final PathNavigationAccessor acc;
		private final @Nullable BlockPos target;
		private final int reach;
		private final Vec3i node;
		private final long timer;
		private final double limit;
		private final boolean stuck;

		Kept(PathNavigationAccessor acc) {
			this.acc = acc;
			this.target = acc.jaymoskingdoms$targetPos();
			this.reach = acc.jaymoskingdoms$reachRange();
			this.node = acc.jaymoskingdoms$timeoutCachedNode();
			this.timer = acc.jaymoskingdoms$timeoutTimer();
			this.limit = acc.jaymoskingdoms$timeoutLimit();
			this.stuck = acc.jaymoskingdoms$isStuck();
		}

		void restore() {
			this.acc.jaymoskingdoms$setTargetPos(this.target);
			this.acc.jaymoskingdoms$setReachRange(this.reach);
			this.acc.jaymoskingdoms$setTimeoutCachedNode(this.node);
			this.acc.jaymoskingdoms$setTimeoutTimer(this.timer);
			this.acc.jaymoskingdoms$setTimeoutLimit(this.limit);
			this.acc.jaymoskingdoms$setStuck(this.stuck);
		}
	}
}
