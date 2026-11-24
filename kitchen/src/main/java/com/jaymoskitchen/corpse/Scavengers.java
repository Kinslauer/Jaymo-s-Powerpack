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
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
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
 * don't. (Andy: "everything should be able to be eaten": players' bodies and kills, monsters' and the rotten dead's
 * bodies too, {@link CorpseEntity#isCarrionFor}; what a player's body had on it is left on the ground for good.)
 * <p>
 * (0.5.5) Zombies go for villagers' bodies first, and a zombie in a village doesn't leave a meal to chase the next
 * villager: only a player (or anything else) it's fighting pulls it off. A villager that's been eaten (by anything)
 * isn't buried: with MCA its tombstone is emptied again ({@link McaGraves}).
 * <p>
 * Andy (2026-11-23, a clip of vultures making off with his body while he watched from Limbo): "vultures should land and
 * pick at the body limb from limb, until disturbed by other mobs or players ... all scavengers and body eaters should go
 * limb at a time, giving players more time to recover their party members or revive a fallen MCA." So a body is eaten a
 * part at a time ({@link CorpseEntity#nextPart}): each arm and leg, then the head, are torn off and eaten there beside
 * it ({@link #CHEW_TICKS}, the part in its mouth), and only then is the trunk torn open. A flier comes down onto the
 * ground to eat; no more than {@link #MAX_EATERS} tear at one body at once, the rest wait their turn. A full scavenger
 * leaves what's left. Feeding, it notices less ({@link #FEEDING_SIGHT}, {@code ScavengerSightMixin}): the skittish ones
 * ({@link #SKITTISH}: vultures, foxes, coyotes) let a sneaking player come closer before they bolt, a moment after they
 * notice ({@link #REACT}), and a bigger scavenger or a monster drives any of them off it ({@link #DISPLACE}). One driven
 * off with a part half torn takes it in its mouth and eats it somewhere else.
 */
public final class Scavengers {
	/** Mobs that eat bodies (data/jaymoskitchen/tags/entity_type/scavengers.json). */
	public static final TagKey<EntityType<?>> SCAVENGERS = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("scavengers"));
	/** How far a scavenger smells a body (blocks), and how far it sees torn-off meat. */
	private static final double SMELL = 16.0, MEAT_SIGHT = 10.0;
	/** Ticks between bites, and bites to tear off each part, then the trunk (plus this many per block of the body's width). */
	private static final int BITE_EVERY = 14, BITES = 5, BITES_PER_WIDTH = 3;
	/** Ticks to eat a part torn off, there by the body; and to eat one it was driven off with, wherever it went. */
	private static final int CHEW_TICKS = 100, CARRY_TICKS = 300;
	/** Scavengers tearing at one body at once; the others wait their turn. */
	private static final int MAX_EATERS = 2;
	/** How fast a flier at the body comes down onto the ground beside it (blocks a tick). */
	private static final double LAND = 0.25;
	/** Scavengers that bolt from a player, rather than stand their ground (data/jaymoskitchen/tags/entity_type/skittish_scavengers.json). */
	public static final TagKey<EntityType<?>> SKITTISH = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("skittish_scavengers"));
	/** Feeding, a scavenger notices anyone at this share of the distance ({@code ScavengerSightMixin}). */
	public static final double FEEDING_SIGHT = 0.4;
	/** How far a skittish one watches for players (blocks, before {@link #FEEDING_SIGHT} and sneaking); ticks it has noticed one before it bolts. */
	private static final double WARY = 12.0;
	private static final int REACT = 20;
	/** A monster, or a scavenger this much bigger, this near (blocks) drives a feeding one off the body. */
	private static final double DISPLACE = 3.0, BIGGER = 1.5;
	/** Ticks a scavenger driven off stays off the bodies. */
	private static final int SCARED_TICKS = 400;
	/** How often a feeding one looks about for trouble (ticks). */
	private static final int LOOK_ABOUT = 5;
	/** How hard it lunges in and yanks back, and how far each yank drags the body (blocks). */
	private static final double LUNGE = 0.18, YANK = 0.22, DRAG = 0.07;
	/** Pieces of meat until it's full, and how long it stays full (ticks; 2400 is two days' quarter). */
	private static final int FULL_AFTER = 3, FULL_TICKS = 4800;
	/** Health back per piece eaten. */
	private static final float HEAL = 3.0F;
	/** How often a scavenger with nothing else to do sniffs for a gut pile (ticks). */
	private static final int GUTS_SNIFF_EVERY = 20;
	/** How often a feeding sun-burner checks whether it stands in the sun (ticks; the check casts rays). */
	private static final int SUN_CHECK_EVERY = 10;
	/** Ticks before it gives up on a body or meat it can't reach. */
	private static final int GIVE_UP = 300;

	/** Meat torn from a body, which any scavenger may eat (a player's own dropped meat isn't in here). */
	private static final Set<ItemEntity> CARRION = Collections.newSetFromMap(new WeakHashMap<>());

	private Scavengers() {
	}

	public static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 == 0) {
				HUNGERS.forEach((mob, hunger) -> hunger.digest(mob));
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && isMouthful(mob.getMainHandItem())) {
				// (a part it was eating when the world was saved: gone with the rest of its meal)
				mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
			}
			if (entity instanceof Mob mob && mob.is(SCAVENGERS)) {
				Hunger hunger = new Hunger();
				HUNGERS.put(mob, hunger);
				// (0.5.5) a zombie stops to feed even mid-chase (ahead of its attack), as long as it's only villagers it's after
				((MobAccessor) mob).jaymoskitchen$goals().addGoal(mob instanceof Zombie ? 1 : 6, new GnawGoal(mob, hunger));
				((MobAccessor) mob).jaymoskitchen$goals().addGoal(6, new EatMeatGoal(mob, hunger));
				((MobAccessor) mob).jaymoskitchen$goals().addGoal(6, new EatPileGoal(mob, hunger, false));
			} else if (entity instanceof Mob mob && mob.is(SALT_LICKERS)) {
				Hunger hunger = new Hunger();
				// (game at a lick has its head down too)
				HUNGERS.put(mob, hunger);
				((MobAccessor) mob).jaymoskitchen$goals().addGoal(6, new EatPileGoal(mob, hunger, true));
			}
		});
	}

	/** Grazers and browsers that come to a salt lick (SaltLickBlock): deer, boar, cattle, horses. */
	public static final TagKey<EntityType<?>> SALT_LICKERS = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("salt_lickers"));
	/** How far off a salt lick draws game. */
	private static final double LICK_DRAW = 24.0;
	/** Game won't come to a lick while a player who isn't sneaking stands this near it. */
	private static final double LICK_WARY = 10.0;
	/** A visit to the lick in this many uses a piece of it up. */
	private static final int LICK_USES_ONE_IN = 4;

	/** Gut piles and salt licks animals may know of, by level: told as one is set down, and again as it lies there. */
	private static final java.util.Map<net.minecraft.server.level.ServerLevel, Set<net.minecraft.core.BlockPos>> PILES = new WeakHashMap<>();

	/** A gut pile lies at {@code pos}: scavengers within {@link #SMELL} of it come to eat. */
	public static void smellPile(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
		PILES.computeIfAbsent(level, l -> new java.util.HashSet<>()).add(pos.immutable());
	}

	private static boolean busy(Mob mob) {
		return mob.getTarget() != null && !feedingOver(mob, mob.getTarget())
			|| mob.isVehicle() || mob.isPassenger() || mob.isLeashed() || mob.isBaby()
			|| mob instanceof TamableAnimal tame && tame.isTame();
	}

	/**
	 * Whether {@code mob} burns in the sun (a zombie) and {@code at} lies open to it while the sun burns, so it won't feed
	 * there: by day it goes for shade (Jaymo's Powers' shelter search) instead of standing over a meal in the sun. Andy
	 * (2026-11-22): "all these zombies should have ran for cover instead of crowding my already dead corpse". (A zombie's
	 * gnawing runs at the same priority as the shelter search, and a goal is only cut off by a higher one, so the meal
	 * held it.)
	 */
	static boolean sunlit(Mob mob, @Nullable Vec3 at) {
		return mob.is(EntityTypeTags.BURN_IN_DAYLIGHT) && mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !mob.isInWaterOrRain()
			&& mob.level().environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, at != null ? at : mob.position())
			&& sunReaches.test(mob, at);
	}

	/**
	 * Whether the sun reaches {@code at} (null: any of the mob's own body). The game's own test (open sky) until Jaymo's
	 * Powers sets its own ({@link #setSunTest}), the one its sun-burning goes by: leaves don't shade there.
	 */
	private static java.util.function.BiPredicate<Mob, @Nullable Vec3> sunReaches =
		(mob, at) -> mob.level().canSeeSky(net.minecraft.core.BlockPos.containing(at != null ? at : mob.getEyePosition()));

	/** (Jaymo's Powers) What decides whether the sun reaches a point, or (given null) any of a mob's body. */
	public static void setSunTest(java.util.function.BiPredicate<Mob, @Nullable Vec3> test) {
		sunReaches = test;
	}

	/** Whether {@code mob} stands in the burning sun: asked now and then while it feeds (every {@link #SUN_CHECK_EVERY} ticks). */
	private static boolean sunCheck(Mob mob) {
		return (mob.tickCount + mob.getId()) % SUN_CHECK_EVERY == 0 && sunlit(mob, null);
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

	/** Each scavenger's appetite, for {@link #feeding} and the parts it's eating. */
	private static final java.util.Map<Mob, Hunger> HUNGERS = new WeakHashMap<>();
	/** Marks the part a scavenger holds in its mouth (it isn't one to keep: see {@link #register}). */
	private static final String MOUTHFUL = "jaymoskitchen:mouthful";

	/**
	 * (2026-11-23) Whether {@code mob} is feeding: tearing at a body, eating a part it tore off, or eating meat or a pile
	 * where it lies. Its mind is on its meal, so it notices less ({@link #FEEDING_SIGHT}).
	 */
	public static boolean feeding(net.minecraft.world.entity.@Nullable Entity mob) {
		Hunger hunger = mob == null ? null : HUNGERS.get(mob);
		return hunger != null && (hunger.eating || hunger.chewing());
	}

	private static boolean isMouthful(net.minecraft.world.item.ItemStack stack) {
		net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(MOUTHFUL, false);
	}

	/** One scavenger's appetite: full for a while after eating. */
	private static final class Hunger {
		long fullUntil;
		int eaten;
		/** At its meal (at a body, meat or a pile, in reach of it). */
		boolean eating;
		/** Driven off: it leaves the bodies be until this game time. */
		long scaredUntil;
		/** A part it tore off, eaten at this game time (-1: none); and what shows in its mouth meanwhile, if its mouth was free. */
		long swallowAt = -1;
		net.minecraft.world.item.@Nullable ItemStack mouthful;

		boolean full(Mob mob) {
			return mob.level().getGameTime() < fullUntil;
		}

		boolean scared(Mob mob) {
			return mob.level().getGameTime() < scaredUntil;
		}

		void ate(Mob mob) {
			if (++eaten >= FULL_AFTER) {
				eaten = 0;
				fullUntil = mob.level().getGameTime() + FULL_TICKS;
			}
		}

		boolean chewing() {
			return swallowAt >= 0;
		}

		/** It has a part torn off in its mouth, eaten {@code ticks} from now. */
		void take(Mob mob, int ticks) {
			swallowAt = mob.level().getGameTime() + ticks;
			if (mob.getMainHandItem().isEmpty()) {
				net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
				tag.putBoolean(MOUTHFUL, true);
				mouthful = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH);
				mouthful.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
				mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, mouthful);
			}
		}

		/** The part it has is eaten, once its time comes (or dropped, if it died or was taken from it). */
		void digest(Mob mob) {
			if (!chewing() || mob.isAlive() && mob.level().getGameTime() < swallowAt) {
				return;
			}
			if (mouthful != null && mob.getMainHandItem() == mouthful) {
				mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
			}
			mouthful = null;
			swallowAt = -1;
			if (mob.isAlive()) {
				mob.heal(HEAL);
				ate(mob);
				com.jaymoskitchen.sound.KitchenSounds.chew(mob, 0.8F, 0.8F);
			}
		}
	}

	/** Goes to a body and eats it, a part at a time, until it's full, the body's gone, or it's driven off. */
	private static final class GnawGoal extends Goal {
		private final Mob mob;
		private final Hunger hunger;
		private @Nullable CorpseEntity body;
		private int ticks, giveUp, noticed;
		/** Driven off: its own running away is left to go on after the goal stops. */
		private boolean fled;

		GnawGoal(Mob mob, Hunger hunger) {
			this.mob = mob;
			this.hunger = hunger;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (busy(mob) || hunger.full(mob) || hunger.scared(mob) || hunger.chewing() || mob.getRandom().nextInt(10) != 0) {
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
			// (a skittish one doesn't come down to it with someone about)
			return body != null && (!(mob.level() instanceof ServerLevel level) || trouble(level) == null);
		}

		@Override
		public void start() {
			ticks = 0;
			noticed = 0;
			fled = false;
			giveUp = GIVE_UP;
			if (body != null) {
				mob.getNavigation().moveTo(body, 1.15);
			}
		}

		@Override
		public boolean canContinueToUse() {
			return body != null && body.isCarrionFor(mob) && giveUp > 0 && !busy(mob) && !hunger.scared(mob)
				&& (hunger.chewing() || !hunger.full(mob));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		private double reach() {
			double r = mob.getBbWidth() / 2.0 + body.getBbWidth() / 2.0 + 0.9;
			return r * r;
		}

		private int needed() {
			return BITES + Mth.floor(body.getBbWidth() * BITES_PER_WIDTH);
		}

		@Override
		public void tick() {
			if (body == null || !(mob.level() instanceof ServerLevel level)) {
				return;
			}
			double dx = body.getX() - mob.getX(), dz = body.getZ() - mob.getZ();
			if (dx * dx + dz * dz > reach()) {
				hunger.eating = false;
				giveUp--;
				ticks = 0;
				mob.getLookControl().setLookAt(body, 30.0F, 30.0F);
				if (mob.getNavigation().isDone()) {
					mob.getNavigation().moveTo(body, 1.15);
				}
				return;
			}
			mob.getNavigation().stop();
			if (!mob.onGround() && !mob.isInWater() && !mob.isInLava()) {
				// a flier comes down onto the ground beside it to eat
				Vec3 v = mob.getDeltaMovement();
				mob.setDeltaMovement(v.x * 0.5, Math.min(v.y, -LAND), v.z * 0.5);
				mob.getLookControl().setLookAt(body, 30.0F, 30.0F);
				return;
			}
			if (ticks % LOOK_ABOUT == 0) {
				net.minecraft.world.entity.Entity who = trouble(level);
				noticed = who == null ? 0 : noticed + LOOK_ABOUT;
				// a monster or a bigger beast at once; a player only once it's sure (it was busy eating)
				if (who != null && (!(who instanceof net.minecraft.world.entity.player.Player) || noticed >= REACT)) {
					flee(level, who);
					return;
				}
			}
			ticks++;
			if (hunger.chewing()) {
				// eating the part it tore off, head down over it
				hunger.eating = true;
				mob.getLookControl().setLookAt(body.getX(), body.getY() - 0.3, body.getZ(), 30.0F, 30.0F);
				if (ticks % 10 == 0) {
					com.jaymoskitchen.sound.KitchenSounds.chew(mob, 0.7F, 0.85F);
					com.jaymoskitchen.KitchenBlood.send(level, body, mob.getX(), mob.getY() + mob.getBbHeight() * 0.4, mob.getZ(), 2, 0.1, 0.05, 0.1, 0.05);
				}
				return;
			}
			if (!hunger.eating && waiting(level)) {
				// its turn hasn't come: it stands by and watches the ones at it
				giveUp--;
				mob.getLookControl().setLookAt(body, 30.0F, 30.0F);
				return;
			}
			hunger.eating = true;
			// worrying it: the head down at the body and shaking from side to side
			Vec3 toBody = new Vec3(dx, 0.0, dz);
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

		/** Whether {@link #MAX_EATERS} others are already at this body. */
		private boolean waiting(ServerLevel level) {
			CorpseEntity at = body;
			return level.getEntitiesOfClass(Mob.class, at.getBoundingBox().inflate(3.0), other -> other != mob && HUNGERS.get(other) instanceof Hunger h
				&& h.eating && other.getNavigation().isDone() && other.distanceToSqr(at) < 9.0).size() >= MAX_EATERS;
		}

		private void bite(ServerLevel level, Vec3 in) {
			// yank back, dragging the body a little after it
			mob.setDeltaMovement(mob.getDeltaMovement().add(in.scale(-YANK)).add(0.0, 0.05, 0.0));
			mob.syncVelocity = true;
			if (mob.getRandom().nextInt(3) == 0) {
				// growling over it
				mob.playAmbientSound();
			}
			int torn = tear(level, mob, body, in, needed());
			if (torn == GONE) {
				body = null;
			} else if (torn > 0) {
				// the part's off: it eats it there
				hunger.take(mob, CHEW_TICKS);
			}
		}

		/**
		 * What drives it off the body, if anything: a monster near (not of its own kind), a scavenger much bigger than it,
		 * and for a skittish one a player it notices ({@link #WARY}; less far while it feeds, {@code ScavengerSightMixin}).
		 * Monsters (zombies, ogres) aren't driven off: their own minds see to who they fight.
		 */
		private net.minecraft.world.entity.@Nullable Entity trouble(ServerLevel level) {
			if (mob instanceof net.minecraft.world.entity.monster.Enemy) {
				return null;
			}
			double size = mob.getBbWidth() * mob.getBbWidth() * mob.getBbHeight();
			for (Mob other : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(DISPLACE), other -> other != mob && other.isAlive()
				&& other.getType() != mob.getType() && !(other instanceof TamableAnimal pet && pet.isTame()))) {
				if (other instanceof net.minecraft.world.entity.monster.Enemy
					|| other.is(SCAVENGERS) && other.getBbWidth() * other.getBbWidth() * other.getBbHeight() > size * BIGGER) {
					return other;
				}
			}
			if (!mob.is(SKITTISH)) {
				return null;
			}
			net.minecraft.world.entity.player.Player player = level.getNearestPlayer(WATCHING, mob, mob.getX(), mob.getEyeY(), mob.getZ());
			return player != null && !player.isCreative() ? player : null;
		}

		/**
		 * Driven off by {@code who}: a part it had half torn comes away in its jaws (it eats that wherever it goes), and a
		 * skittish one bolts (a vulture leaps up into the air, Naturalist's own startle); the rest only leave it be a while.
		 */
		private void flee(ServerLevel level, net.minecraft.world.entity.Entity who) {
			if (!hunger.chewing() && body.isCarrionFor(mob) && body.biteCount() * 2 >= needed()) {
				int part = body.nextPart();
				if (part != 0) {
					body.eatPart(level, part);
					com.jaymoskitchen.KitchenBlood.send(level, body, body.getX(), body.getY() + 0.25, body.getZ(), 15, 0.2, 0.1, 0.2, 0.2);
					hunger.take(mob, CARRY_TICKS);
				}
			}
			hunger.scaredUntil = level.getGameTime() + SCARED_TICKS;
			hunger.eating = false;
			body = null;
			if (mob.is(SKITTISH)) {
				fled = true;
				if (!startle(mob, who.position()) && mob instanceof net.minecraft.world.entity.PathfinderMob runner) {
					Vec3 away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(runner, 16, 7, who.position());
					if (away != null) {
						runner.getNavigation().moveTo(away.x, away.y, away.z, 1.5);
					}
				}
			}
		}

		@Override
		public void stop() {
			body = null;
			hunger.eating = false;
			if (!fled) {
				mob.getNavigation().stop();
			}
			fled = false;
		}
	}

	/** Who a skittish scavenger watches for: anyone it can see within {@link #WARY} (how far follows how well it sees them). */
	private static final net.minecraft.world.entity.ai.targeting.TargetingConditions WATCHING =
		net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().range(WARY);

	/** Each kind's own startle ({@code startle(Vec3)}: Naturalist's vulture leaping into the air), looked up once. */
	private static final java.util.Map<Class<?>, java.util.Optional<java.lang.reflect.Method>> STARTLES = new java.util.concurrent.ConcurrentHashMap<>();

	/** Startles {@code mob} away from {@code from} its own way, if it has one. */
	private static boolean startle(Mob mob, Vec3 from) {
		java.util.Optional<java.lang.reflect.Method> method = STARTLES.computeIfAbsent(mob.getClass(), type -> {
			try {
				return java.util.Optional.of(type.getMethod("startle", Vec3.class));
			} catch (NoSuchMethodException e) {
				return java.util.Optional.empty();
			}
		});
		if (method.isEmpty()) {
			return false;
		}
		try {
			method.get().invoke(mob, from);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** {@link #tear}: the body is gone, torn open; or only bitten. Anything else is the {@code PART_} bit torn off. */
	private static final int GONE = 0, BITTEN = -1;

	/**
	 * One bite into {@code body} by {@code eater}, its jaws coming in along {@code in}: the body is dragged a little after
	 * them and blood flies. Each {@code needed} bites tear off its next part ({@link CorpseEntity#nextPart}: the arms and
	 * legs, then the head), and that's returned; with none left, the trunk is torn open: its meat spilled for the
	 * scavengers, a villager's grave emptied, the body gone ({@link #GONE}). {@link #BITTEN} for a bite and no more.
	 */
	private static int tear(ServerLevel level, net.minecraft.world.entity.Entity eater, CorpseEntity body, Vec3 in, int needed) {
		int bites = body.bite();
		Vec3 drag = in.scale(-DRAG);
		if (level.noCollision(body, body.getBoundingBox().move(drag))) {
			body.setPos(body.getX() + drag.x, body.getY(), body.getZ() + drag.z);
		}
		com.jaymoskitchen.KitchenBlood.send(level, body, body.getX(), body.getY() + 0.25, body.getZ(), 10, 0.2, 0.1, 0.2, 0.15);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, body.getX(), body.getY() + 0.3, body.getZ(), 2, 0.2, 0.1, 0.2, 0.05);
		float pitch = 0.6F + eater.getRandom().nextFloat() * 0.3F;
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.HOSTILE, 1.0F, pitch);
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 0.8F, pitch);
		if (bites < needed) {
			return BITTEN;
		}
		int part = body.nextPart();
		if (part != 0) {
			// a limb (or the head) comes away
			body.eatPart(level, part);
			com.jaymoskitchen.KitchenBlood.send(level, body, body.getX(), body.getY() + 0.3, body.getZ(), 25, 0.3, 0.15, 0.3, 0.2);
			level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 0.8F, 0.7F);
			return part;
		}
		com.jaymoskitchen.KitchenBlood.send(level, body, body.getX(), body.getY() + 0.3, body.getZ(), 40, 0.35, 0.15, 0.35, 0.25);
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 0.5F);
		java.util.Optional<java.util.UUID> villager = body.isVillager() ? body.deadId() : java.util.Optional.empty();
		CARRION.addAll(body.tearApart(level));
		// (0.5.5) nothing left of them to bury
		villager.ifPresent(dead -> McaGraves.unbury(level, dead));
		return GONE;
	}

	/**
	 * Andy: "werewolves can also eat the corpses and parts." For Jaymo's Kingdoms (by name, it doesn't compile against
	 * Kitchen): whether {@code corpse} is a body a werewolf may eat ({@link CorpseEntity#isFleshForDevourer}).
	 */
	public static boolean devourable(net.minecraft.world.entity.Entity corpse) {
		return corpse instanceof CorpseEntity body && body.isFleshForDevourer();
	}

	/**
	 * A werewolf ({@code eater}, a beast or a player wearing one) bites into {@code corpse}: torn at as a scavenger tears
	 * it, a part at a time, {@code needed} bites to each. True when it's been torn open (the body's gone); false for a
	 * bite (or a part off), or for something that isn't a body it may eat.
	 */
	public static boolean devourBite(ServerLevel level, net.minecraft.world.entity.Entity eater, net.minecraft.world.entity.Entity corpse, int needed) {
		if (!(corpse instanceof CorpseEntity body) || !body.isFleshForDevourer()) {
			return false;
		}
		Vec3 in = body.position().subtract(eater.position()).multiply(1.0, 0.0, 1.0);
		in = in.lengthSqr() < 1.0E-4 ? Vec3.ZERO : in.normalize();
		return tear(level, eater, body, in, needed) == GONE;
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
			// (a sun-burner looks only now and then, and not while it stands in the sun: the check casts rays)
			if (unable() || mob.is(EntityTypeTags.BURN_IN_DAYLIGHT) && ((mob.tickCount + mob.getId()) % SUN_CHECK_EVERY != 0 || sunlit(mob, null))) {
				return false;
			}
			meat = null;
			double best = MEAT_SIGHT * MEAT_SIGHT;
			Iterable<ItemEntity> near = tame(mob)
				? mob.level().getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(MEAT_SIGHT), item -> petFood(mob, item))
				: CARRION;
			if (!tame(mob) && (mob.tickCount + mob.getId()) % GUTS_SNIFF_EVERY == 0) {
				// a gut pile (offal left lying, whoever dropped it) is smelt as far off as a body
				for (ItemEntity guts : mob.level().getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(SMELL),
					item -> item.getItem().is(com.jaymoskitchen.butchery.Butchery.OFFAL) && !sunlit(mob, item.position().add(0.0, 0.5, 0.0)))) {
					double distance = guts.distanceToSqr(mob);
					if (meat == null || distance < meat.distanceToSqr(mob)) {
						meat = guts;
					}
				}
				if (meat != null) {
					return true;
				}
			}
			for (ItemEntity item : near) {
				if (item.isAlive() && item.level() == mob.level() && !sunlit(mob, item.position().add(0.0, 0.5, 0.0))) {
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
			return meat != null && meat.isAlive() && giveUp > 0 && !unable() && !sunCheck(mob);
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
				hunger.eating = false;
				if (mob.getNavigation().isDone()) {
					mob.getNavigation().moveTo(meat, 1.1);
				}
				return;
			}
			mob.getNavigation().stop();
			hunger.eating = true;
			if (++chew < 20) {
				if (chew % 5 == 0 && mob.level() instanceof ServerLevel level) {
					level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, meat.getItem().getItem()),
						meat.getX(), meat.getY() + 0.15, meat.getZ(), 4, 0.1, 0.05, 0.1, 0.06);
					com.jaymoskitchen.sound.KitchenSounds.chew(mob, 0.7F, 0.85F);
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
			hunger.eating = false;
			mob.getNavigation().stop();
		}
	}

	/**
	 * Goes to a gut pile set down on the ground and eats it, a piece at a time. Pets don't. With {@code salt}, it is game
	 * coming to a salt lick instead: from farther off, never while a player stands in plain sight by it, and a lick lasts
	 * several visits.
	 */
	private static final class EatPileGoal extends Goal {
		private static final double REACH_SQ = 1.8 * 1.8;
		private final Mob mob;
		private final Hunger hunger;
		private net.minecraft.core.@Nullable BlockPos pile;
		private int giveUp, chew;

		private final boolean salt;

		EatPileGoal(Mob mob, Hunger hunger, boolean salt) {
			this.mob = mob;
			this.hunger = hunger;
			this.salt = salt;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
		}

		private boolean anyPile(net.minecraft.core.BlockPos pos) {
			return mob.level().isLoaded(pos) && mob.level().getBlockState(pos).getBlock() instanceof com.jaymoskitchen.butchery.OffalPileBlock;
		}

		/** A pile of this animal's kind (salt for game, guts for scavengers), with nobody standing over it if it's a lick. */
		private boolean isPile(net.minecraft.core.BlockPos pos) {
			return anyPile(pos) && mob.level().getBlockState(pos).getBlock() instanceof com.jaymoskitchen.butchery.SaltLickBlock == salt
				&& !(salt && watched(pos));
		}

		private boolean watched(net.minecraft.core.BlockPos pos) {
			for (net.minecraft.world.entity.player.Player player : mob.level().players()) {
				if (!player.isSpectator() && !player.isCrouching() && pos.distToCenterSqr(player.position()) < LICK_WARY * LICK_WARY) {
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean canUse() {
			if ((mob.tickCount + mob.getId()) % GUTS_SNIFF_EVERY != 0 || busy(mob) || hunger.full(mob) || sunlit(mob, null)) {
				return false;
			}
			Set<net.minecraft.core.BlockPos> piles = PILES.get(mob.level());
			if (piles == null || piles.isEmpty()) {
				return false;
			}
			pile = null;
			double best = salt ? LICK_DRAW * LICK_DRAW : SMELL * SMELL;
			for (java.util.Iterator<net.minecraft.core.BlockPos> it = piles.iterator(); it.hasNext();) {
				net.minecraft.core.BlockPos pos = it.next();
				double distance = pos.distToCenterSqr(mob.position());
				if (distance >= best) {
					continue;
				}
				if (!anyPile(pos)) {
					it.remove();
					continue;
				}
				if (!isPile(pos) || sunlit(mob, net.minecraft.world.phys.Vec3.atCenterOf(pos))) {
					continue;
				}
				best = distance;
				pile = pos;
			}
			return pile != null;
		}

		@Override
		public void start() {
			giveUp = GIVE_UP;
			chew = 0;
		}

		@Override
		public boolean canContinueToUse() {
			return pile != null && giveUp > 0 && !busy(mob) && !hunger.full(mob) && isPile(pile) && !sunCheck(mob);
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			giveUp--;
			// (the AI ticks a goal again before asking canContinueToUse: the pile may be gone, eaten by another or washed away)
			if (pile == null || !isPile(pile)) {
				return;
			}
			mob.getLookControl().setLookAt(pile.getX() + 0.5, pile.getY() + 0.1, pile.getZ() + 0.5, 60.0F, 60.0F);
			if (pile.distToCenterSqr(mob.position()) > REACH_SQ) {
				chew = 0;
				hunger.eating = false;
				if (mob.getNavigation().isDone()) {
					mob.getNavigation().moveTo(pile.getX() + 0.5, pile.getY(), pile.getZ() + 0.5, 1.1);
				}
				return;
			}
			mob.getNavigation().stop();
			hunger.eating = true;
			if (++chew < 20) {
				if (chew % 5 == 0 && mob.level() instanceof ServerLevel level) {
					level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, mob.level().getBlockState(pile).getBlock().asItem()),
						pile.getX() + 0.5, pile.getY() + 0.15, pile.getZ() + 0.5, 4, 0.1, 0.05, 0.1, 0.06);
					com.jaymoskitchen.sound.KitchenSounds.chew(mob, 0.7F, 0.85F);
				}
				return;
			}
			chew = 0;
			giveUp = GIVE_UP;
			if (!salt || mob.getRandom().nextInt(LICK_USES_ONE_IN) == 0) {
				com.jaymoskitchen.butchery.OffalPileBlock.takeOne(mob.level(), pile, mob.level().getBlockState(pile));
			}
			mob.heal(HEAL);
			hunger.ate(mob);
		}

		@Override
		public void stop() {
			pile = null;
			hunger.eating = false;
			mob.getNavigation().stop();
		}
	}
}
