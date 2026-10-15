package com.jaymoskingdoms;

import java.util.EnumSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.55) Creepers stalk you, after AtomicStryker's Stalker Creepers (Forge/NeoForge only, no licence given: the idea
 * remade here). A creeper that has picked you freezes, staring, while you look at it, and creeps on the moment you look
 * away, so it is the one behind you that gets close. Within {@link #STRIKE} blocks, or once it has started to hiss,
 * looking no longer stops it.
 */
final class Stalkers {
	/** How near the middle of your view a creeper must be to count as watched (cosine; 0.6 is about 53 degrees). */
	static final double WATCHED = 0.6;
	/** Closer than this (blocks) a creeper comes on even while watched. */
	static final double STRIKE = 3.5;
	/** Ahead of the creeper's swell (2) and its chase (4). */
	static final int PRIORITY = 1;

	private Stalkers() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> loaded(entity));
	}

	private static void loaded(Entity entity) {
		if (entity instanceof Creeper creeper) {
			creeper.getGoalSelector().addGoal(PRIORITY, new Freeze(creeper));
		}
	}

	/** Whether {@code player} is looking at {@code creeper}, with nothing in between. */
	static boolean watched(Creeper creeper, Player player) {
		Vec3 to = creeper.getBoundingBox().getCenter().subtract(player.getEyePosition());
		double distance = to.length();
		if (distance < 1.0E-3) {
			return true;
		}
		return player.getViewVector(1.0F).dot(to.scale(1.0 / distance)) > WATCHED && player.hasLineOfSight(creeper);
	}

	/** Holds the creeper still (stealing its legs from the chase) while its target watches it. */
	private static final class Freeze extends Goal {
		private final Creeper creeper;

		Freeze(Creeper creeper) {
			this.creeper = creeper;
			this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.creeper.getTarget();
			return target instanceof Player player && player.isAlive() && !player.isSpectator()
				&& this.creeper.getSwellDir() <= 0 && this.creeper.distanceToSqr(player) > STRIKE * STRIKE
				&& watched(this.creeper, player);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void start() {
			this.creeper.getNavigation().stop();
		}

		@Override
		public void tick() {
			this.creeper.getNavigation().stop();
			if (this.creeper.getTarget() != null) {
				this.creeper.getLookControl().setLookAt(this.creeper.getTarget(), 30.0F, 30.0F);
			}
		}
	}
}
