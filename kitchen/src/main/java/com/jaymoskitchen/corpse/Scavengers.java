package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.mixin.MobAccessor;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.4.35) Predators eat their kills. A wolf, fox, bear or zombie (anything in {@link #SCAVENGERS}) that has nothing to
 * fight goes to a body it killed (or one nobody's player killed) and tears into it: lunging in, worrying it from side to
 * side and dragging it back, blood flying, until it's torn open. The meat spills on the ground with the rest of what it
 * carried, and the scavengers nearby eat the meat off the ground, one piece at a time, until they're full. Tame animals
 * don't, and players' kills are left for the player.
 * <p>
 * (0.5.5) Zombies go for villagers' bodies first, and a zombie in a village doesn't leave a meal to chase the next
 * villager: only a player (or anything else) it's fighting pulls it off. A villager that's been eaten (by anything)
 * isn't buried: with MCA its tombstone is emptied again ({@link McaGraves}).
 */
public final class Scavengers {
	/** Mobs that eat bodies (data/jaymoskitchen/tags/entity_type/scavengers.json). */
	public static final TagKey<EntityType<?>> SCAVENGERS = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("scavengers"));
	/** How far a scavenger smells a body (blocks), and how far it sees torn-off meat. */
	private static final double SMELL = 16.0, MEAT_SIGHT = 10.0;
	/** Ticks between bites, and bites to tear a body open (plus this many per block of the body's width). */
	private static final int BITE_EVERY = 14, BITES = 4, BITES_PER_WIDTH = 4;
	/** How hard it lunges in and yanks back, and how far each yank drags the body (blocks). */
	private static final double LUNGE = 0.18, YANK = 0.22, DRAG = 0.07;
	/** Pieces of meat until it's full, and how long it stays full (ticks; 2400 is two days' quarter). */
	private static final int FULL_AFTER = 3, FULL_TICKS = 4800;
	/** Health back per piece eaten. */
	private static final float HEAL = 3.0F;
	/** Ticks before it gives up on a body or meat it can't reach. */
	private static final int GIVE_UP = 300;

	/** Meat torn from a body, which any scavenger may eat (a player's own dropped meat isn't in here). */
	private static final Set<ItemEntity> CARRION = Collections.newSetFromMap(new WeakHashMap<>());

	private Scavengers() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && mob.is(SCAVENGERS)) {
				Hunger hunger = new Hunger();
				// (0.5.5) a zombie stops to feed even mid-chase (ahead of its attack), as long as it's only villagers it's after
				((MobAccessor) mob).jaymoskitchen$goals().addGoal(mob instanceof Zombie ? 1 : 6, new GnawGoal(mob, hunger));
				((MobAccessor) mob).jaymoskitchen$goals().addGoal(6, new EatMeatGoal(mob, hunger));
			}
		});
	}

	private static boolean busy(Mob mob) {
		return mob.getTarget() != null && !feedingOver(mob, mob.getTarget())
			|| mob.isVehicle() || mob.isPassenger() || mob.isLeashed() || mob.isBaby()
			|| mob instanceof TamableAnimal tame && tame.isTame();
	}

	/** (0.5.5) Whether {@code mob} may feed with {@code target} about: a zombie leaves villagers be while it eats one. */
	private static boolean feedingOver(Mob mob, net.minecraft.world.entity.LivingEntity target) {
		return mob instanceof Zombie && target.is(Corpses.VILLAGERS);
	}

	/** A pet (a tamed wolf): it doesn't scavenge, but it eats meat dropped on the ground for it. */
	private static boolean tame(Mob mob) {
		return mob instanceof TamableAnimal pet && pet.isTame();
	}

	/** Whether a pet will eat this off the ground: its own food (a wolf's is meat), or any meat if it has no say. */
	private static boolean petFood(Mob mob, ItemEntity item) {
		return mob instanceof net.minecraft.world.entity.animal.Animal animal ? animal.isFood(item.getItem())
			: item.getItem().has(net.minecraft.core.component.DataComponents.FOOD);
	}

	/** One scavenger's appetite: full for a while after eating. */
	private static final class Hunger {
		long fullUntil;
		int eaten;

		boolean full(Mob mob) {
			return mob.level().getGameTime() < fullUntil;
		}

		void ate(Mob mob) {
			if (++eaten >= FULL_AFTER) {
				eaten = 0;
				fullUntil = mob.level().getGameTime() + FULL_TICKS;
			}
		}
	}

	/** Goes to a body and tears it open. */
	private static final class GnawGoal extends Goal {
		private final Mob mob;
		private final Hunger hunger;
		private @Nullable CorpseEntity body;
		private int ticks, giveUp;

		GnawGoal(Mob mob, Hunger hunger) {
			this.mob = mob;
			this.hunger = hunger;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (busy(mob) || hunger.full(mob) || mob.getRandom().nextInt(10) != 0) {
				return false;
			}
			List<CorpseEntity> bodies = mob.level().getEntitiesOfClass(CorpseEntity.class, mob.getBoundingBox().inflate(SMELL),
				corpse -> corpse.isCarrionFor(mob));
			body = null;
			double best = Double.MAX_VALUE;
			for (CorpseEntity corpse : bodies) {
				// its own kill first (and for a zombie, a villager)
				double distance = corpse.distanceToSqr(mob) * (corpse.killedBy(mob) ? 0.25 : 1.0)
					* (mob instanceof Zombie && corpse.isVillager() ? 0.25 : 1.0);
				if (distance < best) {
					best = distance;
					body = corpse;
				}
			}
			return body != null;
		}

		@Override
		public void start() {
			ticks = 0;
			giveUp = GIVE_UP;
			if (body != null) {
				mob.getNavigation().moveTo(body, 1.15);
			}
		}

		@Override
		public boolean canContinueToUse() {
			return body != null && body.isCarrionFor(mob) && giveUp > 0 && !busy(mob);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		private double reach() {
			double r = mob.getBbWidth() / 2.0 + body.getBbWidth() / 2.0 + 0.9;
			return r * r;
		}

		@Override
		public void tick() {
			if (body == null || !(mob.level() instanceof ServerLevel level)) {
				return;
			}
			if (mob.distanceToSqr(body) > reach()) {
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
			// worrying it: the head down at the body and shaking from side to side
			Vec3 toBody = body.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
			Vec3 side = toBody.lengthSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(-toBody.z, 0.0, toBody.x).normalize();
			double shake = Mth.sin(ticks * 1.3F) * 0.7;
			mob.getLookControl().setLookAt(body.getX() + side.x * shake, body.getY() - 0.3, body.getZ() + side.z * shake, 90.0F, 90.0F);
			int phase = ticks % BITE_EVERY;
			Vec3 in = toBody.lengthSqr() < 1.0E-4 ? Vec3.ZERO : toBody.normalize();
			if (phase == 1) {
				// lunge in, jaws first
				mob.setDeltaMovement(mob.getDeltaMovement().add(in.scale(LUNGE)));
				mob.syncVelocity = true;
			} else if (phase == 5) {
				bite(level, in);
			}
		}

		private void bite(ServerLevel level, Vec3 in) {
			int bites = body.bite();
			// yank back, dragging the body a little after it
			mob.setDeltaMovement(mob.getDeltaMovement().add(in.scale(-YANK)).add(0.0, 0.05, 0.0));
			mob.syncVelocity = true;
			Vec3 drag = in.scale(-DRAG);
			if (level.noCollision(body, body.getBoundingBox().move(drag))) {
				body.setPos(body.getX() + drag.x, body.getY(), body.getZ() + drag.z);
			}
			BlockParticleOption blood = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
			level.sendParticles(blood, body.getX(), body.getY() + 0.25, body.getZ(), 10, 0.2, 0.1, 0.2, 0.15);
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, body.getX(), body.getY() + 0.3, body.getZ(), 2, 0.2, 0.1, 0.2, 0.05);
			float pitch = 0.6F + mob.getRandom().nextFloat() * 0.3F;
			level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.HOSTILE, 1.0F, pitch);
			level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 0.8F, pitch);
			if (mob.getRandom().nextInt(3) == 0) {
				// growling over it
				mob.playAmbientSound();
			}
			if (bites >= BITES + Mth.floor(body.getBbWidth() * BITES_PER_WIDTH)) {
				level.sendParticles(blood, body.getX(), body.getY() + 0.3, body.getZ(), 40, 0.35, 0.15, 0.35, 0.25);
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 0.5F);
				java.util.Optional<java.util.UUID> villager = body.isVillager() ? body.deadId() : java.util.Optional.empty();
				CARRION.addAll(body.tearApart(level));
				// (0.5.5) nothing left of them to bury
				villager.ifPresent(dead -> McaGraves.unbury(level, dead));
				body = null;
			}
		}

		@Override
		public void stop() {
			body = null;
			mob.getNavigation().stop();
		}
	}

	/**
	 * Eats torn-off meat off the ground, a piece at a time. A pet eats any meat lying near it (so its owner can drop
	 * meat for it), and while it's hurt it's never too full to.
	 */
	private static final class EatMeatGoal extends Goal {
		private static final double REACH_SQ = 1.8 * 1.8;
		private final Mob mob;
		private final Hunger hunger;
		private @Nullable ItemEntity meat;
		private int giveUp, chew;

		EatMeatGoal(Mob mob, Hunger hunger) {
			this.mob = mob;
			this.hunger = hunger;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		private boolean unable() {
			if (tame(mob)) {
				TamableAnimal pet = (TamableAnimal) mob;
				return pet.isOrderedToSit() || pet.getTarget() != null || mob.isPassenger() || mob.isVehicle()
					|| hunger.full(mob) && mob.getHealth() >= mob.getMaxHealth();
			}
			return busy(mob) || hunger.full(mob);
		}

		@Override
		public boolean canUse() {
			if (unable()) {
				return false;
			}
			meat = null;
			double best = MEAT_SIGHT * MEAT_SIGHT;
			Iterable<ItemEntity> near = tame(mob)
				? mob.level().getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(MEAT_SIGHT), item -> petFood(mob, item))
				: CARRION;
			for (ItemEntity item : near) {
				if (item.isAlive() && item.level() == mob.level()) {
					double distance = item.distanceToSqr(mob);
					if (distance < best) {
						best = distance;
						meat = item;
					}
				}
			}
			return meat != null;
		}

		@Override
		public void start() {
			giveUp = GIVE_UP;
			chew = 0;
			if (meat != null) {
				mob.getNavigation().moveTo(meat, 1.1);
			}
		}

		@Override
		public boolean canContinueToUse() {
			return meat != null && meat.isAlive() && giveUp > 0 && !unable();
		}

		@Override
		public void tick() {
			giveUp--;
			if (meat == null) {
				return;
			}
			mob.getLookControl().setLookAt(meat.getX(), meat.getY(), meat.getZ(), 60.0F, 60.0F);
			if (mob.distanceToSqr(meat) > REACH_SQ) {
				chew = 0;
				if (mob.getNavigation().isDone()) {
					mob.getNavigation().moveTo(meat, 1.1);
				}
				return;
			}
			mob.getNavigation().stop();
			if (++chew < 20) {
				if (chew % 5 == 0 && mob.level() instanceof ServerLevel level) {
					level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, meat.getItem().getItem()),
						meat.getX(), meat.getY() + 0.15, meat.getZ(), 4, 0.1, 0.05, 0.1, 0.06);
					mob.playSound(SoundEvents.GENERIC_EAT.value(), 0.7F, 0.7F + mob.getRandom().nextFloat() * 0.3F);
				}
				return;
			}
			chew = 0;
			meat.getItem().shrink(1);
			if (meat.getItem().isEmpty()) {
				CARRION.remove(meat);
				meat.discard();
				meat = null;
			}
			mob.heal(HEAL);
			hunger.ate(mob);
		}

		@Override
		public void stop() {
			meat = null;
			mob.getNavigation().stop();
		}
	}
}
