package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.MobAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Patch 6 of the fighting AI (getting there; the ideas are Hostile Humans', none of its code): detours. A melee fighter whose
 * path to its foe fails (none, or one that stops well short of it) or that has made no headway for {@link #STUCK_TICKS} (no
 * nearer its foe and hardly moved) looks for a way round: spots on rings of {@link #RINGS} blocks round it, sideways of the
 * line to its foe first, the ones nearer the foe first on each ring, a path worked out to each in turn until one gets there.
 * It walks that way, and then its fight takes it on from there. A spot whose path failed, or whose way got it nowhere, is
 * remembered for {@link #FORGET} and not tried again meanwhile.
 * <p>
 * The cost is bounded: at most {@link #PATHS_PER_TRY} paths a try, {@link #TRIES_PER_TICK} tries a tick for all fighters
 * together, and a fighter tries again no sooner than {@link #STUCK_TICKS} after. Folk and illagers through
 * {@link DetourGoal}, MCA's villagers through {@link #brainStep}. Shooters keep to {@link RangedTactics}; never while
 * falling back, out of danger, hiding from a shooter, or making an arrest.
 */
public final class Detours {
	/** No headway this long (ticks): it looks for a way round. A failed path is looked at again this often (ticks). */
	static final int STUCK_TICKS = 40, LOOK = 10;
	/** Headway: this much nearer its foe, or this far moved (blocks). */
	static final double HEADWAY = 1.0;
	/** Nearer its foe than this (blocks) it's in the fight, not getting to it; a path ending this far short of its foe has failed. */
	static final double REACH = 3.0, SHORT = 2.5;
	/** The rings tried (blocks), and the ways round each (degrees off the line to its foe, in the order tried). */
	static final int[] RINGS = {8, 16, 24};
	static final int[] WAYS = {45, -45, 90, -90, 135, -135, 0, 180};
	/** Paths worked out at most in one try; tries at most a tick, all fighters together. */
	static final int PATHS_PER_TRY = 6, TRIES_PER_TICK = 2;
	/** A failed spot is remembered this long (ticks); a spot this near it (blocks) counts as it. */
	static final int FORGET = 100;
	static final double FAILED_NEAR = 3.0;
	/** A way round is walked this long at most (ticks), at this speed; it's there this near (blocks). */
	static final int WAY_LONGEST = 200;
	static final double SPEED = 1.1, THERE = 1.5;

	private record Failed(BlockPos at, long until) {
	}

	private static final class State {
		Vec3 mark;
		double markDist;
		long markTime, nextTry;
		boolean failing;
		@Nullable BlockPos way;
		long wayStart;
		Vec3 wayMark;
		long wayMarkTime;
		final List<Failed> failed = new ArrayList<>();

		State(Vec3 at, double dist, long now) {
			this.mark = at;
			this.markDist = dist;
			this.markTime = now;
		}
	}

	private static final Map<Mob, State> STATES = new WeakHashMap<>();
	private static final Set<Mob> GIVEN = Collections.newSetFromMap(new WeakHashMap<>());
	private static int triesThisTick;

	private Detours() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractIllager illager && GIVEN.add(illager)) {
				((MobAccessor)illager).jaymoskingdoms$goals().addGoal(1, new DetourGoal(illager));
			}
		});
		ServerTickEvents.START_SERVER_TICK.register(server -> triesThisTick = 0);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STATES.clear();
			GIVEN.clear();
		});
	}

	/** A melee fighter on its feet, its own, going for a foe; not falling back, out of danger, hiding, or arresting. */
	private static @Nullable LivingEntity foe(Mob m) {
		if (!m.isAlive() || m.isNoAi() || m.isPassenger() || m.isBaby() || !Temperaments.humanoid(m) || Downed.isDowned(m)
			|| !(m.getNavigation() instanceof GroundPathNavigation)) {
			return null;
		}
		LivingEntity foe = Comrades.target(m);
		if (foe == null || Downed.isDowned(foe) || foe.level() != m.level() || Comrades.ranged(m)) {
			return null;
		}
		if (BreakOff.away(m) || DangerSense.running(m) || Hiding.hiding(m) || RangedTactics.active(m) || Comrades.busy(m) || Arrests.busy(m)
			|| m instanceof PathfinderMob pm && CreeperFlight.fleeing(pm)) {
			return null;
		}
		return foe;
	}

	/** Whether {@code m} is walking a way round just now. */
	static boolean walking(Mob m) {
		State s = STATES.get(m);
		return s != null && s.way != null;
	}

	/** One tick: true if this has {@code m}'s legs (walking a way round). */
	static boolean step(ServerLevel level, Mob m) {
		LivingEntity foe = foe(m);
		long now = level.getGameTime();
		if (foe == null) {
			State s = STATES.remove(m);
			if (s != null && s.way != null) {
				m.getNavigation().stop();
			}
			return false;
		}
		double dist = m.distanceTo(foe);
		State s = STATES.computeIfAbsent(m, k -> new State(m.position(), dist, now));
		s.failed.removeIf(f -> now >= f.until());
		if (s.way != null) {
			return walk(level, m, s, now);
		}
		if (dist < REACH) {
			mark(s, m, dist, now);
			s.failing = false;
			return false;
		}
		boolean stuck = false;
		if (dist < s.markDist - HEADWAY || m.position().distanceTo(s.mark) > HEADWAY) {
			mark(s, m, dist, now);
		} else if (now - s.markTime >= STUCK_TICKS) {
			stuck = true;
			mark(s, m, dist, now);
		}
		if (!stuck && (m.tickCount + m.getId()) % LOOK == 0) {
			// a failed path: none, or one stopping well short of the foe (twice running, not a gap between one path and the next)
			Path path = m.getNavigation().getPath();
			boolean fails = path == null || path.isDone() || !path.canReach() && path.getEndNode() != null
				&& Vec3.atBottomCenterOf(path.getEndNode().asBlockPos()).distanceTo(foe.position()) > SHORT;
			stuck = fails && s.failing;
			s.failing = fails;
		}
		if (!stuck || now < s.nextTry || triesThisTick >= TRIES_PER_TICK) {
			return false;
		}
		triesThisTick++;
		s.nextTry = now + STUCK_TICKS;
		s.failing = false;
		BlockPos way = wayRound(level, m, foe, s, now);
		if (way == null) {
			return false;
		}
		s.way = way;
		s.wayStart = now;
		s.wayMark = m.position();
		s.wayMarkTime = now;
		return true;
	}

	private static void mark(State s, Mob m, double dist, long now) {
		s.mark = m.position();
		s.markDist = dist;
		s.markTime = now;
	}

	/** Walking the way round: true while it still has the legs. */
	private static boolean walk(ServerLevel level, Mob m, State s, long now) {
		BlockPos way = s.way;
		boolean there = m.position().distanceTo(Vec3.atBottomCenterOf(way)) < THERE;
		boolean nowhere = false;
		if (m.position().distanceTo(s.wayMark) > HEADWAY) {
			s.wayMark = m.position();
			s.wayMarkTime = now;
		} else if (now - s.wayMarkTime >= STUCK_TICKS) {
			nowhere = true;
		}
		if (there || nowhere || now - s.wayStart >= WAY_LONGEST || m.getNavigation().isDone() && !there) {
			if (!there) {
				// it got nowhere that way: not tried again for a while
				s.failed.add(new Failed(way, now + FORGET));
			}
			s.way = null;
			mark(s, m, s.markDist, now);
			m.getNavigation().stop();
			return false;
		}
		return true;
	}

	/** A spot round the rings it has a whole, safe path to (walking it, from now); null if none was found in this try. */
	private static @Nullable BlockPos wayRound(ServerLevel level, Mob m, LivingEntity foe, State s, long now) {
		Vec3 here = m.position();
		Vec3 to = foe.position().subtract(here);
		double base = to.horizontalDistanceSqr() < 1.0E-4 ? m.getRandom().nextDouble() * Math.PI * 2.0 : Math.atan2(to.z, to.x);
		int paths = 0;
		for (int ring : RINGS) {
			List<BlockPos> spots = new ArrayList<>();
			for (int way : WAYS) {
				double a = base + Math.toRadians(way);
				BlockPos ground = RangedTactics.ground(level, new Vec3(here.x + Math.cos(a) * ring, here.y, here.z + Math.sin(a) * ring), m.getBlockY());
				if (ground != null && !failed(s, ground) && !BreakOff.harmful(level, ground)) {
					spots.add(ground);
				}
			}
			spots.sort((a, b) -> Double.compare(Vec3.atBottomCenterOf(a).distanceToSqr(foe.position()), Vec3.atBottomCenterOf(b).distanceToSqr(foe.position())));
			for (BlockPos spot : spots) {
				if (paths++ >= PATHS_PER_TRY) {
					return null;
				}
				Path path = m.getNavigation().createPath(spot, 1);
				if (path != null && path.canReach() && safe(path)) {
					m.getNavigation().moveTo(path, SPEED);
					return spot;
				}
				s.failed.add(new Failed(spot, now + FORGET));
			}
		}
		return null;
	}

	private static boolean failed(State s, BlockPos at) {
		for (Failed f : s.failed) {
			if (f.at().closerThan(at, FAILED_NEAR)) {
				return true;
			}
		}
		return false;
	}

	/** No lava, fire or harm on it. */
	private static boolean safe(Path path) {
		for (int i = 0; i < path.getNodeCount(); i++) {
			Node n = path.getNode(i);
			if (n.type == PathType.LAVA || n.type == PathType.FIRE || n.type == PathType.DAMAGING || n.type == PathType.DAMAGE_CAUTIOUS) {
				return false;
			}
		}
		return true;
	}

	// ---- hooks -------------------------------------------------------------------------------------------------------

	/** For an MCA villager (from {@code Workers.step}): true to skip its brain this tick, this walking it round. */
	public static boolean brainStep(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		boolean took = step(level, mob);
		if (took && mob instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		return took;
	}

	/** Detours for a mob with goals (folk, illagers): ahead of its melee, it takes the legs while it walks a way round. */
	public static final class DetourGoal extends Goal {
		private final Mob mob;

		public DetourGoal(Mob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return this.mob.level() instanceof ServerLevel level && step(level, this.mob);
		}

		@Override
		public boolean canContinueToUse() {
			return walking(this.mob);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (this.mob.level() instanceof ServerLevel level) {
				step(level, this.mob);
			}
		}

		@Override
		public void stop() {
			State s = STATES.get(this.mob);
			if (s != null && s.way != null) {
				s.way = null;
				this.mob.getNavigation().stop();
			}
		}
	}
}
