package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.mixin.MobAccessor;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Herds keep together. A weak pull, well below anything an animal has to do: every {@link #PULL_EVERY} ticks one that
 * has fewer than {@link #NEAR_ENOUGH} of its kind within {@link #NEAR} blocks, and some within {@link #FAR}, strolls
 * {@link #STROLL} blocks toward the middle of them. On every muskox ({@link Muskox}, which also drifts toward glow
 * lichen within {@link #LICHEN_RANGE} when its herd is about it), and on the wild grazers in
 * {@code jaymoskingdoms:herds_together} (cows, sheep, pigs, chickens, goats).
 *
 * <p>Never on anyone's animal: one that is leashed, named, tamed or ridden is left alone, and so is a calf (it follows
 * its mother), one in love, one just hurt and one running from a hunter. And never in a pen: the pull is only taken
 * when the animal can path the whole stroll (a fence in the way and it can't), and one that couldn't isn't asked again
 * for {@link #PEN_REST} ticks, so penned animals don't pile into the corner nearest the next field.
 */
public final class HerdPull {
	/** Wild grazers that keep together as muskox do. */
	static final TagKey<EntityType<?>> HERDS_TOGETHER = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("herds_together"));
	/** How often an animal looks for its herd (ticks). */
	static final int PULL_EVERY = 200;
	/** Fewer than this many of its kind this near, and some this far: it's a straggler (blocks). */
	static final int NEAR_ENOUGH = 2;
	static final double NEAR = 6.0, FAR = 24.0, FAR_Y = 8.0;
	/** How far one pull takes it (blocks, and how near the end counts as there), and how fast (of its walk). */
	static final double STROLL = 12.0, SPEED = 0.9;
	static final int STROLL_REACH = 2;
	/** One that couldn't path its stroll (penned, or cut off) isn't asked again for this long (ticks). */
	static final int PEN_REST = 2400;
	/** Muskox: how far they notice lichen (blocks across, up and down), looked for on every this-many-th pull. */
	static final int LICHEN_RANGE = 24, LICHEN_UP = 3, LICHEN_PULLS = 3;
	/** Lichen nearer than this is the grazing's business, not the herd's (blocks). */
	static final double LICHEN_NEAR = 8.0;
	/** The goal's place among a tagged animal's own, when it has no stroll to go by. */
	static final int DEFAULT_PRIORITY = 6;

	private static final Set<PathfinderMob> GIVEN = Collections.newSetFromMap(new WeakHashMap<>());

	private HerdPull() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// (a muskox has the pull among its own goals)
			if (entity instanceof PathfinderMob mob && !(mob instanceof Muskox) && mob.getType().builtInRegistryHolder().is(HERDS_TOGETHER)
				&& GIVEN.add(mob)) {
				((MobAccessor) mob).jaymoskingdoms$goals().addGoal(priority(mob), new PullGoal(mob));
			}
		});
	}

	/** Just ahead of the animal's own idle stroll, behind everything else it does. */
	private static int priority(PathfinderMob mob) {
		for (WrappedGoal goal : ((MobAccessor) mob).jaymoskingdoms$goals().getAvailableGoals()) {
			if (goal.getGoal() instanceof RandomStrollGoal) {
				return Math.max(1, goal.getPriority() - 1);
			}
		}
		return DEFAULT_PRIORITY;
	}

	/** Free to follow its herd: nobody's animal, grown, unhurt, not courting, not running. */
	static boolean free(PathfinderMob mob) {
		return mob.isAlive() && !mob.isBaby() && !mob.isLeashed() && !mob.hasCustomName() && !mob.isPassenger() && !mob.isVehicle()
			&& !(mob instanceof TamableAnimal pet && pet.isTame()) && !(mob instanceof AbstractHorse horse && horse.isTamed())
			&& !(mob instanceof OwnableEntity owned && owned.getOwnerReference() != null)
			&& !(mob instanceof Animal animal && animal.isInLove()) && mob.getLastHurtByMob() == null
			&& !(mob instanceof Muskox muskox && muskox.fleeing());
	}

	/** The pull itself, one per animal. */
	static final class PullGoal extends Goal {
		private final PathfinderMob mob;
		private long lookAt;
		private int pulls;
		private @Nullable Path path;
		private @Nullable BlockPos to;

		PullGoal(PathfinderMob mob) {
			this.mob = mob;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (!(this.mob.level() instanceof ServerLevel level)) {
				return false;
			}
			long now = level.getGameTime();
			if (this.lookAt == 0L) {
				// (each animal on its own beat, so a herd doesn't all look at once)
				this.lookAt = now + this.mob.getRandom().nextInt(PULL_EVERY);
			}
			if (now < this.lookAt) {
				return false;
			}
			this.lookAt = now + PULL_EVERY;
			if (!free(this.mob)) {
				return false;
			}
			this.to = this.herdward(level);
			if (this.to == null && this.mob instanceof Muskox && this.pulls++ % LICHEN_PULLS == 0) {
				BlockPos lichen = Muskox.findLichen(level, this.mob.blockPosition(), LICHEN_RANGE, LICHEN_UP, null);
				if (lichen != null && lichen.distSqr(this.mob.blockPosition()) > LICHEN_NEAR * LICHEN_NEAR) {
					this.to = this.toward(level, Vec3.atCenterOf(lichen));
				}
			}
			if (this.to == null) {
				return false;
			}
			// the whole stroll, or nothing: a fence in the way means a pen
			this.path = this.mob.getNavigation().createPath(this.to, STROLL_REACH);
			if (this.path == null || !this.path.canReach()) {
				this.path = null;
				this.lookAt = now + PEN_REST;
				return false;
			}
			return true;
		}

		/** Where a straggler heads: {@link #STROLL} blocks toward the middle of its kind, or null if it's among them (or alone). */
		private @Nullable BlockPos herdward(ServerLevel level) {
			List<PathfinderMob> kin = level.getEntitiesOfClass(PathfinderMob.class, this.mob.getBoundingBox().inflate(FAR, FAR_Y, FAR),
				other -> other != this.mob && other.isAlive() && other.getType() == this.mob.getType());
			int near = 0, far = 0;
			double x = 0.0, y = 0.0, z = 0.0;
			for (PathfinderMob other : kin) {
				double sq = other.distanceToSqr(this.mob);
				if (sq <= NEAR * NEAR) {
					near++;
				}
				if (sq <= FAR * FAR) {
					far++;
					x += other.getX();
					y += other.getY();
					z += other.getZ();
				}
			}
			if (far == 0 || near >= NEAR_ENOUGH) {
				return null;
			}
			return this.toward(level, new Vec3(x / far, y / far, z / far));
		}

		/** The ground {@link #STROLL} blocks off toward {@code goal} (or at it, if it's nearer); null where the land isn't loaded. */
		private @Nullable BlockPos toward(ServerLevel level, Vec3 goal) {
			Vec3 from = this.mob.position();
			Vec3 off = goal.subtract(from).multiply(1.0, 0.0, 1.0);
			Vec3 at = off.lengthSqr() <= STROLL * STROLL ? goal : from.add(off.normalize().scale(STROLL));
			BlockPos column = BlockPos.containing(at.x, from.y, at.z);
			return level.isLoaded(column) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column) : null;
		}

		@Override
		public void start() {
			if (this.to == null) {
				return;
			}
			if (this.mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
				// (a goat walks by its brain: it's told where, once)
				BehaviorUtils.setWalkAndLookTargetMemories(this.mob, this.to, (float) SPEED, STROLL_REACH);
				this.path = null;
			} else {
				this.mob.getNavigation().moveTo(this.path, SPEED);
			}
		}

		@Override
		public boolean canContinueToUse() {
			return this.path != null && !this.mob.getNavigation().isDone() && free(this.mob);
		}

		@Override
		public void stop() {
			this.path = null;
			this.to = null;
		}
	}
}
