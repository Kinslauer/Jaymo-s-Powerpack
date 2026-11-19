package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.MobAccessor;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * Better pathing for golems (Andy, on gv_golem_float: "just give them better pathing and the ability to jump up and gain
 * altitude in water"). Iron golems (the vanilla ones, here, and the town golems, {@link TownGolem}, which call
 * {@link #improve}):
 * <ul>
 * <li>they path <b>around</b> water where there is a way ({@link PathType#WATER} costs {@link #WATER_COST}), and are not
 * afraid of what is only shallow;</li>
 * <li>they <b>step up</b> ledges of a block and a bit ({@link #STEP_HEIGHT}), not a fence's worth of dithering at a kerb;</li>
 * <li>and in water they <b>jump up and gain altitude</b> instead of walking the riverbed: the swimming goal bobs them up, and
 * when a bank stops them ({@link ClimbOut}) they heave themselves over it.</li>
 * </ul>
 */
public final class GolemPathing {
	/** What water costs a golem's path (the game's default for a walker is 8); the height of a step it takes without jumping (blocks). */
	static final float WATER_COST = 16.0F;
	static final double STEP_HEIGHT = 1.25;
	/** The heave out of the water onto a bank: the upward speed (blocks a tick), and its push forward. */
	static final double HEAVE = 0.42, HEAVE_FORWARD = 0.1;

	private static final Set<Mob> GIVEN = Collections.newSetFromMap(new WeakHashMap<>());

	private GolemPathing() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// (the town golems add theirs in their own goals)
			if (entity instanceof IronGolem golem && GIVEN.add(golem)) {
				var goals = ((MobAccessor)golem).jaymoskingdoms$goals();
				goals.addGoal(0, new FloatGoal(golem));
				goals.addGoal(1, new ClimbOut(golem));
				improve(golem);
			}
		});
	}

	/** Water costs more on a golem's path, and it steps up higher. */
	public static void improve(AbstractGolem golem) {
		golem.setPathfindingMalus(PathType.WATER, WATER_COST);
		AttributeInstance step = golem.getAttribute(Attributes.STEP_HEIGHT);
		if (step != null && step.getBaseValue() < STEP_HEIGHT) {
			step.setBaseValue(STEP_HEIGHT);
		}
	}

	/** In water with a bank in the way: heaves itself up and over it. */
	public static final class ClimbOut extends Goal {
		private final Mob golem;

		public ClimbOut(Mob golem) {
			this.golem = golem;
			this.setFlags(EnumSet.of(Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			return this.golem.isInWater() && this.golem.horizontalCollision;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			Vec3 v = this.golem.getDeltaMovement();
			Vec3 ahead = this.golem.getLookAngle().multiply(1.0, 0.0, 1.0);
			this.golem.setDeltaMovement(v.x + ahead.x * HEAVE_FORWARD, Math.max(v.y, HEAVE), v.z + ahead.z * HEAVE_FORWARD);
		}
	}
}
