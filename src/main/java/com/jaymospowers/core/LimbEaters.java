package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.mixin.MobGoalsAccessor;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "bears, wolves, and other animals should eat the limbs on the ground." A meat-eater with nothing to fight
 * smells a severed head or limb ({@link SeveredPart}) lying near, goes to it, and eats it where it lies: head down,
 * worrying it from side to side, a bite at a time, until it's gone (a bone is sometimes left). Then it's fed for a while
 * and leaves the next one be. Pets don't (their owners feed them), nor the young, the leashed, the ridden.
 *
 * <p>Who eats: everything in {@code jaymospowers:limb_eaters} (wolves, foxes, bears, big cats, Jaymo's Kitchen's
 * scavengers, which take in the ogres and zombies), and any other mod's animal that eats meat (one whose
 * food is raw beef, pork, mutton, chicken or rabbit), so new wolves and bears join in without a list. Only parts with
 * flesh on them ({@link SeveredPart#edible}); a living body, downed or not, is never touched here.
 *
 * <p>Andy: "is there a bird that can grab and fly off with the limbs? like a vulture?" Carrion birds
 * ({@code jaymospowers:limb_carriers}: Naturalist's vulture) don't eat where it lies: one spots a part from far off,
 * swoops down, takes it up in its talons and flies off with it to somewhere well away from anyone ({@link CarryOffGoal}),
 * lands, and eats it there. Struck on the way, it lets go.
 */
public final class LimbEaters {
	/** Animals that eat severed parts (data/jaymospowers/tags/entity_type/limb_eaters.json). */
	public static final TagKey<EntityType<?>> EATERS = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("limb_eaters"));
	/** Birds that carry parts off to eat (data/jaymospowers/tags/entity_type/limb_carriers.json). */
	public static final TagKey<EntityType<?>> CARRIERS = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("limb_carriers"));
	/** How far a carrion bird spots a part (sideways, and down from the sky); how far off it takes it (blocks, at least and at most). */
	static final double SPOT = 32.0, SPOT_DOWN = 32.0, CARRY_MIN = 20.0, CARRY_MAX = 40.0;
	/** It keeps this far from anyone when it chooses where to eat; it gives up flying there after this long (ticks). */
	static final double QUIET = 16.0;
	static final int CARRY_GIVE_UP = 600;
	/** How far it smells a part (blocks), and how far above or below it it'll go for one. */
	static final double SMELL = 16.0, SMELL_UP = 6.0;
	/** One look in this many ticks (while idle) for something to eat. */
	static final int LOOK_ODDS = 10;
	/** Ticks between bites; how hard it lunges in at each. */
	static final int BITE_EVERY = 12;
	static final double LUNGE = 0.12;
	/** Health back for a part eaten. */
	static final float HEAL = 3.0F;
	/** Parts until it's full, and how long it stays full (ticks). */
	static final int FULL_AFTER = 2, FULL_TICKS = 4800;
	/** Ticks before it gives up on a part it can't reach. */
	static final int GIVE_UP = 300;

	private static final net.minecraft.resources.Identifier WEREWOLF = net.minecraft.resources.Identifier.fromNamespaceAndPath("jaymoskingdoms", "werewolf");

	/** Mobs already given the goal (goals aren't saved, so a reload gives it again to the new mob). */
	private static final Map<Mob, Boolean> GIVEN = new WeakHashMap<>();

	private LimbEaters() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && eats(mob) && GIVEN.put(mob, true) == null) {
				// beside a scavenger's gnawing at bodies (6): only one runs at a time
				if (mob.getType().builtInRegistryHolder().is(CARRIERS)) {
					// level with its fleeing, so a player coming near doesn't make it drop what it's carrying
					((MobGoalsAccessor) mob).jaymospowers$goals().addGoal(1, new CarryOffGoal(mob));
				} else {
					((MobGoalsAccessor) mob).jaymospowers$goals().addGoal(6, new EatLimbGoal(mob));
				}
			}
		});
	}

	/** Whether {@code mob} eats severed parts: on the list, or an animal that eats meat. */
	public static boolean eats(Mob mob) {
		if (mob.getType().builtInRegistryHolder().is(CARRIERS)) {
			return true;
		}
		if (WEREWOLF.equals(EntityType.getKey(mob.getType()))) {
			// Jaymo's Kingdoms' werewolves eat in their own way (Devouring), and only as the beast, never by day's face
			return false;
		}
		if (mob.getType().builtInRegistryHolder().is(EATERS)) {
			return true;
		}
		if (!(mob instanceof Animal animal)) {
			return false;
		}
		try {
			return animal.isFood(new ItemStack(Items.BEEF)) || animal.isFood(new ItemStack(Items.PORKCHOP))
				|| animal.isFood(new ItemStack(Items.MUTTON)) || animal.isFood(new ItemStack(Items.CHICKEN))
				|| animal.isFood(new ItemStack(Items.RABBIT));
		} catch (RuntimeException e) {
			// another mod's animal that can't answer while it's loading
			return false;
		}
	}

	private static boolean busy(Mob mob) {
		return mob.getTarget() != null || mob.isVehicle() || mob.isPassenger() || mob.isLeashed() || mob.isBaby() || mob.isNoAi()
			|| mob instanceof TamableAnimal tame && tame.isTame();
	}

	/** Goes to a severed part and eats it. */
	private static final class EatLimbGoal extends Goal {
		private final Mob mob;
		private @Nullable SeveredPart part;
		private int ticks, giveUp, eaten;
		private long fullUntil;

		EatLimbGoal(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (busy(mob) || mob.level().getGameTime() < fullUntil || mob.getRandom().nextInt(LOOK_ODDS) != 0) {
				return false;
			}
			part = null;
			double best = Double.MAX_VALUE;
			for (SeveredPart near : mob.level().getEntitiesOfClass(SeveredPart.class, mob.getBoundingBox().inflate(SMELL, SMELL_UP, SMELL),
				SeveredPart::edible)) {
				double distance = near.distanceToSqr(mob);
				if (distance < best) {
					best = distance;
					part = near;
				}
			}
			return part != null;
		}

		@Override
		public void start() {
			ticks = 0;
			giveUp = GIVE_UP;
			if (part != null) {
				mob.getNavigation().moveTo(part.getX(), part.getY(), part.getZ(), 1.1);
			}
		}

		@Override
		public boolean canContinueToUse() {
			return part != null && part.edible() && giveUp > 0 && !busy(mob);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		private double reach() {
			double r = mob.getBbWidth() / 2.0 + 0.9;
			return r * r;
		}

		@Override
		public void tick() {
			if (part == null || !(mob.level() instanceof ServerLevel level)) {
				return;
			}
			if (mob.distanceToSqr(part) > reach()) {
				giveUp--;
				ticks = 0;
				mob.getLookControl().setLookAt(part, 30.0F, 30.0F);
				if (mob.getNavigation().isDone()) {
					mob.getNavigation().moveTo(part.getX(), part.getY(), part.getZ(), 1.1);
				}
				return;
			}
			mob.getNavigation().stop();
			ticks++;
			// head down at it, worrying it from side to side
			Vec3 to = part.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
			Vec3 side = to.lengthSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(-to.z, 0.0, to.x).normalize();
			double shake = Mth.sin(ticks * 1.3F) * 0.5;
			mob.getLookControl().setLookAt(part.getX() + side.x * shake, part.getY() - 0.2, part.getZ() + side.z * shake, 90.0F, 90.0F);
			int phase = ticks % BITE_EVERY;
			if (phase == 1 && to.lengthSqr() > 1.0E-4) {
				mob.setDeltaMovement(mob.getDeltaMovement().add(to.normalize().scale(LUNGE)));
				mob.needsSync = true;
			} else if (phase == 5) {
				if (mob.getRandom().nextInt(3) == 0) {
					// growling over it
					mob.playAmbientSound();
				}
				if (part.bite(level, mob)) {
					mob.heal(HEAL);
					if (++eaten >= FULL_AFTER) {
						eaten = 0;
						fullUntil = level.getGameTime() + FULL_TICKS;
					}
					part = null;
				}
			}
		}

		@Override
		public void stop() {
			part = null;
			mob.getNavigation().stop();
		}
	}

	/** A carrion bird: down to a part, up with it in its talons, off somewhere quiet, and it eats it there. */
	private static final class CarryOffGoal extends Goal {
		private final Mob bird;
		private @Nullable SeveredPart part;
		private @Nullable Vec3 eatAt;
		private int ticks, giveUp;
		private long fullUntil;
		private float healthWhenTaken;

		CarryOffGoal(Mob bird) {
			this.bird = bird;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		private @Nullable SeveredPart carried() {
			for (net.minecraft.world.entity.Entity passenger : bird.getPassengers()) {
				if (passenger instanceof SeveredPart held) {
					return held;
				}
			}
			return null;
		}

		@Override
		public boolean canUse() {
			eatAt = null;
			part = carried();
			if (part != null) {
				// still holding one (after a reload): carry on with it
				return true;
			}
			if (bird.getTarget() != null || bird.isLeashed() || bird.isBaby() || bird.isNoAi() || bird.isPassenger()
				|| bird.level().getGameTime() < fullUntil || bird.getRandom().nextInt(LOOK_ODDS) != 0) {
				return false;
			}
			double best = Double.MAX_VALUE;
			for (SeveredPart near : bird.level().getEntitiesOfClass(SeveredPart.class,
				new net.minecraft.world.phys.AABB(bird.getX() - SPOT, bird.getY() - SPOT_DOWN, bird.getZ() - SPOT, bird.getX() + SPOT, bird.getY() + 8.0, bird.getZ() + SPOT),
				SeveredPart::edible)) {
				double distance = near.distanceToSqr(bird);
				if (distance < best) {
					best = distance;
					part = near;
				}
			}
			return part != null;
		}

		@Override
		public void start() {
			ticks = 0;
			giveUp = GIVE_UP;
			if (part != null && part.getVehicle() == bird) {
				takenOff();
			} else if (part != null) {
				bird.getNavigation().moveTo(part.getX(), part.getY() + 0.3, part.getZ(), 1.3);
			}
		}

		@Override
		public boolean canContinueToUse() {
			return part != null && !part.isRemoved() && giveUp > 0 && bird.getTarget() == null
				&& (part.getVehicle() == bird || part.edible());
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		/** Up it goes with it, toward somewhere well away from anyone. */
		private void takenOff() {
			healthWhenTaken = bird.getHealth();
			giveUp = CARRY_GIVE_UP;
			eatAt = quietPlace();
			bird.getNavigation().moveTo(eatAt.x, eatAt.y + 1.0, eatAt.z, 1.4);
		}

		/** Somewhere on the ground {@link #CARRY_MIN} to {@link #CARRY_MAX} off, away from the nearest player. */
		private Vec3 quietPlace() {
			ServerLevel level = (ServerLevel) bird.level();
			net.minecraft.world.entity.player.Player near = level.getNearestPlayer(bird, CARRY_MAX * 2.0);
			Vec3 best = null;
			double bestScore = -Double.MAX_VALUE;
			for (int tries = 0; tries < 8; tries++) {
				float yaw = bird.getRandom().nextFloat() * Mth.TWO_PI;
				double far = CARRY_MIN + bird.getRandom().nextDouble() * (CARRY_MAX - CARRY_MIN);
				double x = bird.getX() + Mth.cos(yaw) * far, z = bird.getZ() + Mth.sin(yaw) * far;
				int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
				Vec3 at = new Vec3(x, y, z);
				if (!level.getFluidState(net.minecraft.core.BlockPos.containing(x, y - 1, z)).isEmpty()) {
					continue;
				}
				double score = near == null ? far : near.position().distanceTo(at);
				if (score > bestScore) {
					bestScore = score;
					best = at;
				}
			}
			return best != null ? best : bird.position().add(CARRY_MIN, 0.0, 0.0);
		}

		@Override
		public void tick() {
			if (part == null || !(bird.level() instanceof ServerLevel level)) {
				return;
			}
			ticks++;
			if (part.getVehicle() == bird) {
				// carrying it: struck, it lets go; there, it lands and puts it down to eat
				if (bird.getHealth() < healthWhenTaken) {
					part.stopRiding();
					part = null;
					return;
				}
				giveUp--;
				if (eatAt == null) {
					takenOff();
				}
				double flat = Mth.square(bird.getX() - eatAt.x) + Mth.square(bird.getZ() - eatAt.z);
				if (flat < 4.0 && bird.getY() - eatAt.y < 2.5 || giveUp <= 0 || bird.onGround() && flat < 36.0) {
					part.stopRiding();
					part.setPos(bird.getX(), bird.getY(), bird.getZ());
					// eats it here: the next look finds it at its feet
					bird.getNavigation().stop();
					giveUp = GIVE_UP;
					ticks = 0;
					return;
				}
				if (bird.getNavigation().isDone() || ticks % 40 == 0) {
					bird.getNavigation().moveTo(eatAt.x, eatAt.y + 1.0, eatAt.z, 1.4);
				}
				return;
			}
			double reach = bird.getBbWidth() / 2.0 + 1.0;
			if (bird.distanceToSqr(part) > reach * reach) {
				giveUp--;
				bird.getLookControl().setLookAt(part, 30.0F, 30.0F);
				if (bird.getNavigation().isDone() || ticks % 40 == 0) {
					bird.getNavigation().moveTo(part.getX(), part.getY() + 0.3, part.getZ(), 1.3);
				}
				return;
			}
			if (eatAt == null) {
				// down on it: snatched up, and away
				if (part.snatchedBy(bird)) {
					level.playSound(null, bird.getX(), bird.getY(), bird.getZ(), net.minecraft.sounds.SoundEvents.PHANTOM_FLAP,
						net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 0.7F);
					bird.playAmbientSound();
					takenOff();
				} else {
					part = null;
				}
				return;
			}
			// put down where it chose: head down, tearing at it
			bird.getNavigation().stop();
			bird.getLookControl().setLookAt(part.getX(), part.getY() - 0.2, part.getZ(), 90.0F, 90.0F);
			if (ticks % BITE_EVERY == 5 && part.bite(level, bird)) {
				bird.heal(HEAL);
				fullUntil = level.getGameTime() + FULL_TICKS;
				part = null;
			}
		}

		@Override
		public void stop() {
			if (part != null && part.getVehicle() == bird) {
				// made to stop (a fight, a fright): it lets go
				part.stopRiding();
			}
			part = null;
			eatAt = null;
			bird.getNavigation().stop();
		}
	}
}
