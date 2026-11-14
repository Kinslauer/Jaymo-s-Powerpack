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
 *
 * <p>Andy: "they should circle dead bodies in midair". Before it comes down to any of the dead (a body lying anywhere,
 * or a part), a carrion bird circles high over it a while ({@link CircleGoal}), round and round, then drops down
 * beside it: to snatch a part, or to tear at a body as a scavenger. Only then will it take a part. Any others about
 * spot it too, so a kill draws a ring of them.
 *
 * <p>Andy: "everything should be able to be eaten, the animals should start with your limbs, so that if you are revived
 * you now need a new one." The downed (players too, once they're down and nobody's standing over them) are eaten alive
 * ({@link TearGoal}): an arm or a leg at a time, worried at until it comes off ({@link Dismember#sever}, lost as any
 * severed part is, to grow back or be replaced), then eaten. With no limbs left it's the body, bite by bite, until
 * they die. A player on their feet within {@link #GUARD} blocks keeps them off; carried, they're safe.
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
	/** Circling: how far it spots the dead from (sideways, and down), how wide and how high it circles over them (blocks, least and spread), how fast it goes round (blocks a tick). */
	static final double CIRCLE_SPOT = 48.0, CIRCLE_SPOT_DOWN = 48.0, CIRCLE_RADIUS = 6.0, CIRCLE_RADIUS_SPREAD = 5.0,
		CIRCLE_HEIGHT = 10.0, CIRCLE_HEIGHT_SPREAD = 8.0, CIRCLE_SPEED = 0.35;
	/** How long it circles (ticks, least and spread); then how long it has to come down and eat before it would circle again. */
	static final int CIRCLE_TICKS = 400, CIRCLE_SPREAD = 400, DESCEND_TICKS = 1200;
	/** The downed: kept safe by a player standing within this (blocks); bites to tear a limb off; hurt done by a bite on a limb, and on the body once the limbs are gone. */
	static final double GUARD = 6.0;
	static final int LIMB_BITES = 5;
	static final float LIMB_BITE_HURT = 0.5F, BODY_BITE_HURT = 2.0F;
	/** One look in this many ticks for the dead to circle. */
	static final int CIRCLE_LOOK_ODDS = 20;
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
	/** A carrion bird that has circled the dead may come down to them until this game time. */
	private static final Map<Mob, Long> CIRCLED = new WeakHashMap<>();

	private LimbEaters() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && eats(mob) && GIVEN.put(mob, true) == null) {
				// beside a scavenger's gnawing at bodies (6): only one runs at a time
				if (mob.getType().builtInRegistryHolder().is(CARRIERS)) {
					// level with its fleeing, so a player coming near doesn't make it drop what it's carrying
					((MobGoalsAccessor) mob).jaymospowers$goals().addGoal(1, new CarryOffGoal(mob));
					// beside its own search for food and perching (2), above its high wandering (3)
					((MobGoalsAccessor) mob).jaymospowers$goals().addGoal(2, new CircleGoal(mob));
					((MobGoalsAccessor) mob).jaymospowers$goals().addGoal(2, new TearGoal(mob, true));
				} else {
					((MobGoalsAccessor) mob).jaymospowers$goals().addGoal(6, new EatLimbGoal(mob));
					((MobGoalsAccessor) mob).jaymospowers$goals().addGoal(6, new TearGoal(mob, false));
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

	/** Whether {@code bird} has circled the dead lately and may come down to them. */
	static boolean circled(Mob bird) {
		Long until = CIRCLED.get(bird);
		return until != null && bird.level().getGameTime() < until;
	}

	/** The dead a carrion bird circles: a body lying still (Jaymo's Kitchen's), or a part it could eat. */
	static boolean dead(net.minecraft.world.entity.Entity entity) {
		if (entity.isRemoved() || entity.isPassenger()) {
			return false;
		}
		if (entity instanceof SeveredPart part) {
			return part.edible();
		}
		// the dying too: the downed, whoever's near them
		return com.jaymospowers.power.gravity.GravityPower.isCorpse(entity)
			|| entity instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive() && com.jaymospowers.core.wounds.Downed.isDowned(living);
	}

	/** Whether {@code entity} is downed and nobody's keeping the animals off it: a body to eat alive. */
	static boolean prey(net.minecraft.world.entity.Entity entity) {
		if (!(entity instanceof net.minecraft.world.entity.LivingEntity body) || !body.isAlive() || !com.jaymospowers.core.wounds.Downed.isDowned(body)
			|| body.isPassenger() || Carry.isCarried(body)
			|| body instanceof net.minecraft.world.entity.player.Player player && (player.isCreative() || player.isSpectator())) {
			return false;
		}
		for (net.minecraft.world.entity.player.Player guard : body.level().players()) {
			if (guard != body && guard.isAlive() && !guard.isSpectator() && !com.jaymospowers.core.wounds.Downed.isDowned(guard)
				&& guard.distanceToSqr(body) < GUARD * GUARD) {
				return false;
			}
		}
		return true;
	}

	/** The limbs {@code body} still has that can be torn off (none for what can't lose parts). */
	static java.util.List<Integer> limbs(net.minecraft.world.entity.LivingEntity body) {
		java.util.List<Integer> left = new java.util.ArrayList<>();
		if (Dismember.canLoseParts(body)) {
			int missing = Dismember.missing(body);
			for (int part : new int[] {Dismember.RIGHT_ARM, Dismember.LEFT_ARM, Dismember.RIGHT_LEG, Dismember.LEFT_LEG}) {
				if ((missing & part) == 0) {
					left.add(part);
				}
			}
		}
		return left;
	}

	/** Eats the downed alive: their limbs first, one at a time, then the rest of them. */
	private static final class TearGoal extends Goal {
		private final Mob mob;
		/** A carrion bird: only once it's circled them. */
		private final boolean circles;
		private net.minecraft.world.entity.@Nullable LivingEntity body;
		private int ticks, giveUp, bites, limb;

		TearGoal(Mob mob, boolean circles) {
			this.mob = mob;
			this.circles = circles;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (busy(mob) && !(mob.getTarget() != null && prey(mob.getTarget())) || circles && !circled(mob)
				|| !mob.getPassengers().isEmpty() || mob.getRandom().nextInt(LOOK_ODDS) != 0) {
				return false;
			}
			body = null;
			double best = Double.MAX_VALUE;
			for (net.minecraft.world.entity.LivingEntity near : mob.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
				mob.getBoundingBox().inflate(SMELL, SMELL_UP, SMELL), e -> e != mob && prey(e))) {
				double distance = near.distanceToSqr(mob);
				if (distance < best) {
					best = distance;
					body = near;
				}
			}
			return body != null;
		}

		@Override
		public void start() {
			ticks = 0;
			bites = 0;
			limb = 0;
			giveUp = GIVE_UP;
			if (body != null) {
				mob.getNavigation().moveTo(body, 1.15);
			}
		}

		@Override
		public boolean canContinueToUse() {
			return body != null && prey(body) && giveUp > 0 && mob.getPassengers().isEmpty();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (body == null || !(mob.level() instanceof ServerLevel level)) {
				return;
			}
			double r = mob.getBbWidth() / 2.0 + body.getBbWidth() / 2.0 + 0.9;
			if (mob.distanceToSqr(body) > r * r) {
				giveUp--;
				ticks = 0;
				mob.getLookControl().setLookAt(body, 30.0F, 30.0F);
				if (mob.getNavigation().isDone()) {
					mob.getNavigation().moveTo(body, 1.15);
				}
				return;
			}
			mob.getNavigation().stop();
			ticks++;
			Vec3 to = body.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
			Vec3 side = to.lengthSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(-to.z, 0.0, to.x).normalize();
			double shake = Mth.sin(ticks * 1.3F) * 0.6;
			mob.getLookControl().setLookAt(body.getX() + side.x * shake, body.getY() + 0.2, body.getZ() + side.z * shake, 90.0F, 90.0F);
			int phase = ticks % BITE_EVERY;
			if (phase == 1 && to.lengthSqr() > 1.0E-4) {
				mob.setDeltaMovement(mob.getDeltaMovement().add(to.normalize().scale(LUNGE)));
				mob.needsSync = true;
			} else if (phase == 5) {
				bite(level);
			}
		}

		private void bite(ServerLevel level) {
			java.util.List<Integer> left = limbs(body);
			if (limb == 0 || !left.contains(limb)) {
				// the limbs first, whichever it gets its jaws round
				limb = left.isEmpty() ? -1 : left.get(mob.getRandom().nextInt(left.size()));
				bites = 0;
			}
			float pitch = 0.6F + mob.getRandom().nextFloat() * 0.3F;
			level.playSound(null, body.getX(), body.getY(), body.getZ(), net.minecraft.sounds.SoundEvents.GENERIC_EAT.value(),
				net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, pitch);
			level.playSound(null, body.getX(), body.getY(), body.getZ(), net.minecraft.sounds.SoundEvents.SLIME_SQUISH,
				net.minecraft.sounds.SoundSource.HOSTILE, 0.7F, pitch);
			if (BladeBlood.bleeds(body)) {
				Vec3 at = limb > 0 ? Dismember.stump(body, limb) : body.position().add(0.0, 0.3, 0.0);
				level.sendParticles(com.jaymospowers.core.blood.BloodParticles.fleck(BladeBlood.color(body), 1.0F), at.x, at.y, at.z, 8, 0.15, 0.1, 0.15, 0.08);
			}
			if (mob.getRandom().nextInt(3) == 0) {
				mob.playAmbientSound();
			}
			body.hurtServer(level, level.damageSources().mobAttack(mob), limb > 0 ? LIMB_BITE_HURT : BODY_BITE_HURT);
			if (limb > 0 && ++bites >= LIMB_BITES && body.isAlive()) {
				// torn off: it lies there to be eaten (by this one, or whoever's quicker)
				Dismember.sever(body, limb, mob);
				limb = 0;
				body = null;
			}
		}

		@Override
		public void stop() {
			body = null;
			mob.getNavigation().stop();
		}
	}

	/** High over the dead, round and round, a while; then it may come down to them. */
	private static final class CircleGoal extends Goal {
		private final Mob bird;
		private net.minecraft.world.entity.@Nullable Entity over;
		private double radius, height, angle;
		private int ticks, length;
		private boolean clockwise;

		CircleGoal(Mob bird) {
			this.bird = bird;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (bird.getTarget() != null || bird.isLeashed() || bird.isBaby() || bird.isNoAi() || bird.isPassenger() || circled(bird)
				|| !bird.getPassengers().isEmpty() || bird.getRandom().nextInt(CIRCLE_LOOK_ODDS) != 0) {
				return false;
			}
			over = null;
			double best = Double.MAX_VALUE;
			for (net.minecraft.world.entity.Entity e : bird.level().getEntities(bird, new net.minecraft.world.phys.AABB(
				bird.getX() - CIRCLE_SPOT, bird.getY() - CIRCLE_SPOT_DOWN, bird.getZ() - CIRCLE_SPOT,
				bird.getX() + CIRCLE_SPOT, bird.getY() + 8.0, bird.getZ() + CIRCLE_SPOT), LimbEaters::dead)) {
				double distance = e.distanceToSqr(bird);
				if (distance < best) {
					best = distance;
					over = e;
				}
			}
			return over != null;
		}

		@Override
		public void start() {
			ticks = 0;
			length = CIRCLE_TICKS + bird.getRandom().nextInt(CIRCLE_SPREAD);
			radius = CIRCLE_RADIUS + bird.getRandom().nextDouble() * CIRCLE_RADIUS_SPREAD;
			height = CIRCLE_HEIGHT + bird.getRandom().nextDouble() * CIRCLE_HEIGHT_SPREAD;
			clockwise = bird.getRandom().nextBoolean();
			angle = over == null ? 0.0 : Math.atan2(bird.getZ() - over.getZ(), bird.getX() - over.getX());
			bird.getNavigation().stop();
		}

		@Override
		public boolean canContinueToUse() {
			return over != null && dead(over) && ticks < length && bird.getTarget() == null && bird.getPassengers().isEmpty();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (over == null) {
				return;
			}
			ticks++;
			angle += (clockwise ? -1.0 : 1.0) * CIRCLE_SPEED / radius;
			double x = over.getX() + Math.cos(angle) * radius, z = over.getZ() + Math.sin(angle) * radius;
			// a little rise and fall as it rides the air
			double y = over.getY() + height + Math.sin(ticks * 0.05) * 1.5;
			bird.getMoveControl().setWantedPosition(x, y, z, 1.0);
			double ahead = angle + (clockwise ? -0.4 : 0.4);
			bird.getLookControl().setLookAt(over.getX() + Math.cos(ahead) * radius, y, over.getZ() + Math.sin(ahead) * radius, 10.0F, 10.0F);
			if (ticks % 160 == 0 && bird.getRandom().nextInt(2) == 0) {
				bird.playAmbientSound();
			}
		}

		@Override
		public void stop() {
			if (over != null && ticks >= length) {
				// circled long enough: now it may come down to them
				CIRCLED.put(bird, bird.level().getGameTime() + DESCEND_TICKS);
				if (!(over instanceof SeveredPart)) {
					// a body: down beside it, to tear at it as the scavengers do
					bird.getNavigation().moveTo(over.getX(), over.getY(), over.getZ(), 1.2);
				}
			}
			over = null;
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
				|| bird.level().getGameTime() < fullUntil || !circled(bird) || bird.getRandom().nextInt(LOOK_ODDS) != 0) {
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
