package com.jaymoskingdoms;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Everyone runs from a creeper about to blow (Andy: "everyone should run from creepers ready to explode"). Once a
 * creeper starts to hiss (its fuse lit, or flint and steel put to it), every mob within {@link #RANGE} blocks of it
 * ({@link #CHARGED_RANGE} for a charged one) drops what it was doing and runs clear: villagers and our folk, guards,
 * animals, pets, monsters, raiders. They stop when the creeper is gone (blown or killed) or calms down.
 * <p>
 * Who stays put: other creepers (so a pack still comes on together), the downed and the bound (they can't), anyone
 * riding or being carried, a mount someone is steering, mobs without AI, and the big ones that can take a blast
 * ({@link #STEADY_HEALTH} max health or more: iron golems, ravagers, bosses), so a golem still smashes the creeper.
 * A sleeper wakes; a pet told to sit gets up and runs, and sits again after.
 * <p>
 * The run is a goal ahead of every other move goal on each mob ({@link #PRIORITY}), added as the mob loads (as
 * {@link Stalkers} does for creepers); a mob with a walking brain (villagers, piglins, goats) is sent through its walk
 * target instead, as {@link Herds} does. Hissing creepers are looked for every {@link #EVERY} ticks.
 */
final class CreeperFlight {
	/** How near a hissing creeper a mob runs from it (blocks); a charged creeper's blast reaches twice as far. */
	static final double RANGE = 8.0, CHARGED_RANGE = 14.0;
	/** How far past the edge of the blast they aim to stop (blocks). */
	static final double CLEAR = 3.0;
	/** Mobs with at least this much max health hold their ground: a plain creeper's blast can't kill them. */
	static final float STEADY_HEALTH = 100.0F;
	/** How fast they run (a speed multiplier on their walk). */
	static final double SPEED = 1.6;
	/** How often hissing creepers are looked for; how long a mob keeps running after the last sighting (ticks). */
	static final int EVERY = 2, HOLD = 10;
	/** Ahead of every other goal, including priority-0 ones (it takes only the legs, so floating still works). */
	static final int PRIORITY = -1;
	/** Tries at a spot clear of the blast before taking the farthest one found. */
	static final int TRIES = 6;

	private record Threat(Creeper creeper, long until) {
	}

	private static final Set<Creeper> CREEPERS = new HashSet<>();
	private static final Map<PathfinderMob, Threat> THREATS = new WeakHashMap<>();

	private CreeperFlight() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Creeper creeper) {
				CREEPERS.add(creeper);
			}
			if (entity instanceof PathfinderMob mob && !(entity instanceof Creeper)) {
				mob.getGoalSelector().addGoal(PRIORITY, new Run(mob));
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Creeper creeper) {
				CREEPERS.remove(creeper);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(CreeperFlight::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CREEPERS.clear();
			THREATS.clear();
		});
	}

	/** About to blow: its fuse is burning (it hisses and swells). */
	static boolean hissing(Creeper creeper) {
		return creeper.isAlive() && !creeper.isRemoved() && (creeper.getSwellDir() > 0 || creeper.isIgnited());
	}

	static double range(Creeper creeper) {
		return creeper.isPowered() ? CHARGED_RANGE : RANGE;
	}

	private static void tick(MinecraftServer server) {
		if (CREEPERS.isEmpty() || server.getTickCount() % EVERY != 0) {
			return;
		}
		for (Creeper creeper : CREEPERS.toArray(new Creeper[0])) {
			if (creeper.isRemoved()) {
				CREEPERS.remove(creeper);
			} else if (hissing(creeper) && creeper.level() instanceof ServerLevel level) {
				warn(level, creeper);
			}
		}
	}

	/** Everyone near {@code creeper} who can run is told to. */
	private static void warn(ServerLevel level, Creeper creeper) {
		double r = range(creeper);
		long until = level.getGameTime() + HOLD;
		for (PathfinderMob mob : level.getEntitiesOfClass(PathfinderMob.class, creeper.getBoundingBox().inflate(r),
			m -> m != creeper && m.distanceToSqr(creeper) <= r * r && runs(m))) {
			Threat was = THREATS.get(mob);
			// the nearest hissing creeper is the one to run from
			if (was == null || was.creeper() == creeper || !hissing(was.creeper())
				|| mob.distanceToSqr(creeper) < mob.distanceToSqr(was.creeper())) {
				THREATS.put(mob, new Threat(creeper, until));
			}
		}
	}

	/** Whether {@code mob} runs from a blast at all (see the class note for who stays put). */
	static boolean runs(PathfinderMob mob) {
		if (!mob.isAlive() || mob.isNoAi() || mob instanceof Creeper || mob.isPassenger()
			|| mob.getMaxHealth() >= STEADY_HEALTH || Downed.isDowned(mob) || Captives.bound(mob)) {
			return false;
		}
		Entity rider = mob.getControllingPassenger();
		return !(rider instanceof Player);
	}

	/** The creeper {@code mob} is running from, if any. */
	private static @Nullable Creeper threat(PathfinderMob mob) {
		Threat t = THREATS.get(mob);
		if (t == null) {
			return null;
		}
		if (!hissing(t.creeper()) || mob.level().getGameTime() > t.until() || t.creeper().level() != mob.level()) {
			THREATS.remove(mob);
			return null;
		}
		return t.creeper();
	}

	/** The run: it takes the mob's legs while a creeper near it hisses. */
	private static final class Run extends Goal {
		private final PathfinderMob mob;
		private @Nullable Creeper from;
		private @Nullable Vec3 to;

		Run(PathfinderMob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (THREATS.isEmpty()) {
				return false;
			}
			this.from = threat(this.mob);
			return this.from != null && runs(this.mob);
		}

		@Override
		public boolean canContinueToUse() {
			Creeper c = threat(this.mob);
			if (c != null) {
				this.from = c;
			}
			return c != null && runs(this.mob);
		}

		@Override
		public boolean isInterruptable() {
			return false;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			if (this.mob.isSleeping()) {
				this.mob.stopSleeping();
			}
			if (this.mob instanceof TamableAnimal pet && pet.isInSittingPose()) {
				// (SitWhenOrderedToGoal sits it down again once the run ends, if it was told to sit)
				pet.setInSittingPose(false);
			}
			this.to = null;
			this.aim();
		}

		@Override
		public void tick() {
			if (this.from == null) {
				return;
			}
			Vec3 at = this.from.position();
			double clear = range(this.from) + CLEAR;
			boolean stale = this.to == null || this.to.distanceToSqr(at) < clear * clear;
			if (stale || this.mob.getNavigation().isDone() || this.mob.tickCount % 10 == 0 || this.walkTaken()) {
				this.aim();
			}
		}

		/** A brain's other behaviours (work, gossip, a bed) set a walk target of their own: take it back. */
		private boolean walkTaken() {
			if (this.to == null || !this.mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
				return false;
			}
			BlockPos ours = BlockPos.containing(this.to);
			return this.mob.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
				.map(w -> !w.getTarget().currentBlockPosition().equals(ours)).orElse(true);
		}

		@Override
		public void stop() {
			this.from = null;
			this.to = null;
			this.mob.getNavigation().stop();
			if (this.mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
				this.mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			}
		}

		/** Picks (or keeps) a spot clear of the blast and heads for it. */
		private void aim() {
			Creeper c = this.from;
			if (c == null) {
				return;
			}
			Vec3 at = c.position();
			double clear = range(c) + CLEAR;
			if (this.to == null || this.to.distanceToSqr(at) < clear * clear) {
				Vec3 best = null;
				double far = -1.0;
				int reach = (int)Math.ceil(clear) + 4;
				for (int i = 0; i < TRIES; i++) {
					Vec3 v = DefaultRandomPos.getPosAway(this.mob, reach, 7, at);
					if (v == null) {
						continue;
					}
					double d = v.distanceToSqr(at);
					if (d > far) {
						far = d;
						best = v;
					}
					if (d >= clear * clear) {
						break;
					}
				}
				if (best == null) {
					// boxed in: at least step straight away from it
					Vec3 away = this.mob.position().subtract(at).multiply(1.0, 0.0, 1.0);
					if (away.lengthSqr() < 1.0E-4) {
						away = new Vec3(this.mob.getRandom().nextDouble() - 0.5, 0.0, this.mob.getRandom().nextDouble() - 0.5);
					}
					best = this.mob.position().add(away.normalize().scale(clear));
				}
				this.to = best;
			}
			Vec3 t = this.to;
			if (this.mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
				BehaviorUtils.setWalkAndLookTargetMemories(this.mob, BlockPos.containing(t), (float)SPEED, 0);
			} else {
				this.mob.getNavigation().moveTo(t.x, t.y, t.z, SPEED);
			}
		}
	}
}
