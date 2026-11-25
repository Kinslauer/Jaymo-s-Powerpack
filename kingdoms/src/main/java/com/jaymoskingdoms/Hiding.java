package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.MobAccessor;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Patch 6 of the fighting AI (getting there; the ideas are Hostile Humans', none of its code): cover from shooters. A melee
 * fighter shot at ({@link #UNDER_FIRE}) by a shooter it has no way to (no path that gets there: up on a wall, across water)
 * goes to a spot within {@link #COVER_REACH} where something stands between the shooter and its head and middle
 * ({@link Cover#hideSpot}), crouches there for {@link #WAIT}, then tries again: its fight takes it on, and it isn't sent to
 * hide again for {@link #AGAIN}. Out of cover before then if the shooter is down or gone. Only a foe's shots count (not
 * one of its own side's). The way to cover is walked from the goal's start, and worked out again ({@link #REPATHS} at most)
 * if the walk ends short of it; its trial paths share {@link PathProbes}' budget. Folk and illagers through
 * {@link HideGoal}, MCA's villagers through {@link #brainStep}; never while falling back, out of danger, running from a
 * blast or arresting.
 */
public final class Hiding {
	/** Shot within this long (ticks): under fire. */
	static final int UNDER_FIRE = 60;
	/** A shooter this far off at most (blocks) is hidden from. */
	static final double SHOOTER_RANGE = 40.0;
	/** Cover is looked for this near (blocks), in this many tries; at most this long walking to it (ticks), at this speed. */
	static final double COVER_REACH = 10.0, SPEED = 1.25, THERE = 1.0;
	static final int COVER_TRIES = 16, GO_LONGEST = 100;
	/** Behind cover this long (ticks); not sent to hide again for this long after (ticks). */
	static final int WAIT = 40, AGAIN = 60;
	/** Looked at this often (ticks). */
	static final int LOOK = 10;
	/** A walk to cover that ends short of it is worked out again at most this many times. */
	static final int REPATHS = 2;

	private record Shot(LivingEntity by, long at) {
	}

	private static final class State {
		final LivingEntity shooter;
		final BlockPos spot;
		final long start;
		long thereAt = -1L;
		/** The way to cover, found and not yet walked (walked from the goal's start, or at once for a villager). */
		@Nullable Path pending;
		int repaths;

		State(LivingEntity shooter, BlockPos spot, long start) {
			this.shooter = shooter;
			this.spot = spot;
			this.start = start;
		}
	}

	private static final Map<Mob, Shot> SHOTS = new WeakHashMap<>();
	private static final Map<Mob, State> STATES = new WeakHashMap<>();
	private static final Map<Mob, Long> NOT_BEFORE = new WeakHashMap<>();
	private static final Set<Mob> GIVEN = Collections.newSetFromMap(new WeakHashMap<>());

	private Hiding() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Mob m && Temperaments.humanoid(m) && source.is(DamageTypeTags.IS_PROJECTILE) && source.getEntity() instanceof LivingEntity by
				&& by != m && !m.isAlliedTo(by) && !Comrades.side(by).equals(Comrades.side(m))) {
				// (a foe's shot: not a friend's stray arrow)
				SHOTS.put(m, new Shot(by, m.level().getGameTime()));
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractIllager illager && GIVEN.add(illager)) {
				((MobAccessor)illager).jaymoskingdoms$goals().addGoal(1, new HideGoal(illager));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SHOTS.clear();
			STATES.clear();
			NOT_BEFORE.clear();
			GIVEN.clear();
		});
	}

	/** Whether {@code m} is going to cover or waiting behind it. */
	static boolean hiding(Mob m) {
		return STATES.containsKey(m);
	}

	/** On its feet and its own, a melee fighter, not falling back, out of danger or arresting. */
	private static boolean able(Mob m) {
		return m.isAlive() && !m.isNoAi() && !m.isPassenger() && !m.isBaby() && Temperaments.humanoid(m) && !Downed.isDowned(m)
			&& m.getNavigation() instanceof GroundPathNavigation && !Comrades.ranged(m) && !BreakOff.away(m) && !DangerSense.running(m)
			&& !Arrests.busy(m) && !Comrades.busy(m) && !(m instanceof PathfinderMob pm && CreeperFlight.fleeing(pm));
	}

	private static boolean standing(@Nullable LivingEntity e, Mob m) {
		return e != null && e.isAlive() && !e.isRemoved() && e.level() == m.level() && !Downed.isDowned(e) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
			&& e.distanceToSqr(m) < SHOOTER_RANGE * SHOOTER_RANGE;
	}

	/** Looks at whether {@code m} is under fire from a shooter it can't get to, and sends it to cover. */
	private static void consider(ServerLevel level, Mob m, long now) {
		if ((m.tickCount + m.getId()) % LOOK != 0 || now < NOT_BEFORE.getOrDefault(m, 0L)) {
			return;
		}
		Shot shot = SHOTS.get(m);
		if (shot == null) {
			return;
		}
		if (now - shot.at() > UNDER_FIRE || !standing(shot.by(), m)) {
			SHOTS.remove(m);
			return;
		}
		if (Comrades.target(m) == null || !able(m) || !PathProbes.room(2)) {
			// (out of trial paths this tick: looked at again next time)
			return;
		}
		LivingEntity shooter = shot.by();
		PathProbes.take();
		Path to = PathProbes.to(m, shooter, 1);
		if (to != null && to.canReach()) {
			// it can get to the shooter: it goes for it as it would (and isn't asked again for a second)
			NOT_BEFORE.put(m, now + 2 * LOOK);
			return;
		}
		NOT_BEFORE.put(m, now + AGAIN);
		BlockPos spot = Cover.hideSpot(level, m, shooter, COVER_REACH, COVER_TRIES);
		if (spot == null) {
			return;
		}
		PathProbes.take();
		Path path = PathProbes.to(m, spot, 0);
		if (path == null || !path.canReach()) {
			return;
		}
		State s = new State(shooter, spot, now);
		s.pending = path;
		STATES.put(m, s);
	}

	/** One tick: true if this has {@code m}'s legs (going to cover, or behind it). */
	static boolean step(ServerLevel level, Mob m) {
		long now = level.getGameTime();
		State s = STATES.get(m);
		if (s == null) {
			consider(level, m, now);
			s = STATES.get(m);
			if (s == null) {
				return false;
			}
		}
		if (!able(m) || !standing(s.shooter, m)) {
			end(m, now);
			return false;
		}
		if (s.pending != null) {
			// found, not yet set walking
			return true;
		}
		boolean there = m.position().distanceTo(Vec3.atBottomCenterOf(s.spot)) < THERE;
		if (s.thereAt < 0L) {
			if (!there && m.getNavigation().isDone() && !Cover.hides(level, m, s.shooter, m.position()) && s.repaths < REPATHS && PathProbes.take()) {
				// its walk ended short of cover: worked out again from here
				s.repaths++;
				m.getNavigation().moveTo(s.spot.getX() + 0.5, s.spot.getY(), s.spot.getZ() + 0.5, 0, SPEED);
			}
			if (there || m.getNavigation().isDone() && Cover.hides(level, m, s.shooter, m.position())) {
				s.thereAt = now;
				m.getNavigation().stop();
			} else if (now - s.start >= GO_LONGEST || m.getNavigation().isDone()) {
				end(m, now);
				return false;
			} else {
				return true;
			}
		}
		if (now - s.thereAt >= WAIT) {
			// two seconds behind it: out again, and at it
			end(m, now);
			return false;
		}
		m.setShiftKeyDown(true);
		m.getLookControl().setLookAt(s.shooter, 30.0F, 30.0F);
		return true;
	}

	private static void end(Mob m, long now) {
		if (STATES.remove(m) != null) {
			m.setShiftKeyDown(false);
			NOT_BEFORE.put(m, now + AGAIN);
		}
	}

	// ---- hooks -------------------------------------------------------------------------------------------------------

	/** For an MCA villager (from {@code Workers.step}): true to skip its brain this tick, this having its body. */
	public static boolean brainStep(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}
		boolean took = step(level, mob);
		if (took) {
			go(mob);
			if (mob instanceof Villager v) {
				v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			}
		}
		return took;
	}

	/** Sets it walking to the cover it found, if it hasn't yet. */
	private static void go(Mob mob) {
		State s = STATES.get(mob);
		if (s != null && s.pending != null) {
			Path path = s.pending;
			s.pending = null;
			PathProbes.follow(mob, path, 0, SPEED);
		}
	}

	/** Cover from shooters for a mob with goals (folk, illagers): ahead of its melee, it takes the legs and the eyes while it hides. */
	public static final class HideGoal extends Goal {
		private final Mob mob;

		public HideGoal(Mob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			return this.mob.level() instanceof ServerLevel level && step(level, this.mob);
		}

		@Override
		public boolean canContinueToUse() {
			return hiding(this.mob);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			go(this.mob);
		}

		@Override
		public void tick() {
			if (this.mob.level() instanceof ServerLevel level && step(level, this.mob)) {
				go(this.mob);
			}
		}

		@Override
		public void stop() {
			if (this.mob.level() instanceof ServerLevel level) {
				end(this.mob, level.getGameTime());
			}
		}
	}
}
