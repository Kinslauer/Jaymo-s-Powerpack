package com.jaymoskingdoms.wild;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-25): "crocodiles should be among the first, they should deathroll with a mob in its jaws, dealing damage
 * every violent turn, then drag it's prey into the water to feed. like it's natural behavior". A crocodile or an
 * alligator ({@link #JAWS}) that bites something small enough for it ({@link Jaws#mostPrey} times its own bulk) keeps
 * it in its jaws: it rolls over with it, a full turn every {@link #ROLL_EVERY} ticks ({@link #ROLLING}, drawn by
 * {@code client/CrocRoll}: both mods' models are drawn by Minecraft's own living renderer, so both can be turned over),
 * each one hurting the prey ({@link Jaws#rollDamage}), splashing in water. After {@link Jaws#rolls} turns it drags the prey to the nearest water ({@link #WATER_SEARCH}), and there it holds it
 * under and rolls on. If the prey dies in its jaws, it drags the body into the water instead and lets it go there, to
 * eat it (Jaymo's Kitchen's scavengers: crocodiles and alligators are in its {@code scavengers} tag). Hurting it hard
 * enough ({@link #BREAK_FREE}) makes it let go, as stabbing at a real one's eyes does.
 * <p>
 * The two differ as the real ones do ({@link Jaws}): the crocodile is the bigger killer, taking beasts up to four times
 * its bulk and rolling hardest; the alligator takes smaller prey, rolls longer and lighter, and hunts beasts only from
 * the water ({@link Crocodiles#takePrey}).
 */
public final class DeathRoll {
	/**
	 * How each kind hunts: the biggest prey it takes in its jaws (a share of its own bulk, width x width x height), turns
	 * before it drags, harm of each turn, and whether it hunts beasts only from the water.
	 */
	record Jaws(double mostPrey, int rolls, float rollDamage, boolean fromWater) {
	}

	/** Cube Animals' crocodile (a Nile or saltwater one: takes a horse or an ox) and Naturalist's alligator (a deer, a dog). */
	static final Map<Identifier, Jaws> JAWS = Map.of(
		Identifier.fromNamespaceAndPath("cubeanimals", "crocodile"), new Jaws(4.0, 4, 3.5F, false),
		Identifier.fromNamespaceAndPath("naturalist", "alligator"), new Jaws(2.5, 5, 2.5F, true));
	/** Synced to every client while it turns over: the game time its present turn began ({@code client/CrocRoll}). */
	public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Long> ROLLING =
		net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(com.jaymoskingdoms.JaymosKingdoms.id("croc_rolling"),
			builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG,
				net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.all()));
	/** Jaymo's Kitchen's body. */
	static final Identifier CORPSE = Identifier.fromNamespaceAndPath("jaymoskitchen", "corpse");
	/** Ticks a turn over takes (and between them). */
	public static final int ROLL_EVERY = 12;
	/** In the water it turns this much slower (it holds the prey under between). */
	static final int WATER_ROLL_EVERY = 24;
	/** Harm taken since the bite that makes it let go. */
	static final float BREAK_FREE = 6.0F;
	/** How far it looks for water to drag to (blocks), and how long it drags before it gives up (ticks). */
	static final int WATER_SEARCH = 16, DRAG_TICKS = 300;
	/** How long it waits for the body after the prey dies (ticks), and how near the body must lie (blocks). */
	static final int BODY_WAIT = 40;
	static final double BODY_NEAR = 3.0;
	/** Drag speed (of its own), and the furthest the prey may get from its jaws before it's lost. */
	static final double DRAG_SPEED = 1.0, LOST = 4.0;

	private enum Phase { ROLL, DRAG, HOLD }

	private static final class Grip {
		Entity prey;
		Phase phase = Phase.ROLL;
		int turns, ticks, dragged;
		final float healthAtBite;
		/** The prey died: waiting for its body (game time it died), or the body is held. */
		long diedAt = -1;
		boolean body;
		@Nullable BlockPos water;

		Grip(Entity prey, float healthAtBite) {
			this.prey = prey;
			this.healthAtBite = healthAtBite;
		}
	}

	private static final Map<Mob, Grip> GRIPS = new WeakHashMap<>();

	private DeathRoll() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && source.getEntity() instanceof Mob croc && source.getDirectEntity() == croc && jaws(croc)
				&& !croc.isBaby() && !GRIPS.containsKey(croc) && entity.isAlive() && !held(entity) && !jaws(entity)
				&& !entity.isPassenger() && bulk(entity) <= bulk(croc) * jawsOf(croc).mostPrey()) {
				GRIPS.put(croc, new Grip(entity, croc.getHealth()));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (GRIPS.isEmpty()) {
				return;
			}
			for (Map.Entry<Mob, Grip> e : GRIPS.entrySet().toArray(Map.Entry[]::new)) {
				if (!tick(e.getKey(), e.getValue())) {
					GRIPS.remove(e.getKey());
					e.getKey().removeAttached(ROLLING);
				}
			}
		});
	}

	/** Whether {@code e} is a crocodile or an alligator. */
	static boolean jaws(Entity e) {
		return JAWS.containsKey(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
	}

	/** How {@code croc} hunts ({@link Jaws}); the crocodile's if it's neither. */
	static Jaws jawsOf(Entity croc) {
		Jaws jaws = JAWS.get(BuiltInRegistries.ENTITY_TYPE.getKey(croc.getType()));
		return jaws != null ? jaws : JAWS.get(Identifier.fromNamespaceAndPath("cubeanimals", "crocodile"));
	}

	/** Whether something is in a crocodile's jaws. */
	public static boolean held(Entity e) {
		for (Grip grip : GRIPS.values()) {
			if (grip.prey == e) {
				return true;
			}
		}
		return false;
	}

	static double bulk(Entity e) {
		return e.getBbWidth() * e.getBbWidth() * e.getBbHeight();
	}

	/** One tick of a crocodile's grip; false once it has let go. */
	private static boolean tick(Mob croc, Grip grip) {
		if (!croc.isAlive() || !(croc.level() instanceof ServerLevel level) || croc.getHealth() <= grip.healthAtBite - BREAK_FREE) {
			return false;
		}
		grip.ticks++;
		if (grip.diedAt >= 0 && !grip.body) {
			// the prey died in its jaws: its body, once it lies there, is dragged off instead
			Entity body = bodyNear(level, grip.prey.position());
			if (body == null) {
				return level.getGameTime() - grip.diedAt < BODY_WAIT;
			}
			grip.prey = body;
			grip.body = true;
			grip.phase = Phase.DRAG;
			grip.dragged = 0;
		}
		Entity prey = grip.prey;
		if (!grip.body && prey instanceof LivingEntity living && !living.isAlive()) {
			grip.diedAt = level.getGameTime();
			return true;
		}
		if (prey.isRemoved() || prey.level() != level || prey.distanceToSqr(croc) > (LOST + croc.getBbWidth()) * (LOST + croc.getBbWidth())) {
			return false;
		}
		boolean wet = croc.isInWater();
		if (grip.phase == Phase.DRAG && wet) {
			if (grip.body) {
				// in the water with the body: it lets go of it there and eats (Kitchen's scavengers)
				place(croc, prey);
				return false;
			}
			grip.phase = Phase.HOLD;
		}
		switch (grip.phase) {
			case ROLL -> {
				croc.getNavigation().stop();
				if (grip.ticks % ROLL_EVERY == 0) {
					turn(level, croc, prey, grip);
					if (++grip.turns >= jawsOf(croc).rolls()) {
						grip.phase = wet ? Phase.HOLD : Phase.DRAG;
					}
				}
			}
			case DRAG -> {
				croc.removeAttached(ROLLING);
				if (grip.water == null) {
					grip.water = water(level, croc.blockPosition());
					if (grip.water == null) {
						// no water near: it rolls on where it is
						grip.phase = Phase.ROLL;
						grip.turns = 0;
						break;
					}
				}
				if (++grip.dragged > DRAG_TICKS) {
					return false;
				}
				if (croc.getNavigation().isDone()) {
					croc.getNavigation().moveTo(grip.water.getX() + 0.5, grip.water.getY(), grip.water.getZ() + 0.5, DRAG_SPEED);
				}
			}
			case HOLD -> {
				croc.getNavigation().stop();
				if (grip.ticks % WATER_ROLL_EVERY == 0) {
					turn(level, croc, prey, grip);
				}
			}
		}
		place(croc, prey);
		return true;
	}

	/**
	 * A violent turn: the crocodile turns over once ({@link #ROLLING}, drawn by its clients over {@link #ROLL_EVERY}
	 * ticks), wrenching what it holds, which is hurt by it.
	 */
	private static void turn(ServerLevel level, Mob croc, Entity prey, Grip grip) {
		croc.setAttached(ROLLING, level.getGameTime());
		if (prey instanceof LivingEntity living) {
			living.hurtServer(level, level.damageSources().mobAttack(croc), jawsOf(croc).rollDamage());
		}
		boolean wet = croc.isInWater();
		level.playSound(null, croc.getX(), croc.getY(), croc.getZ(), wet ? SoundEvents.PLAYER_SPLASH : SoundEvents.PLAYER_ATTACK_STRONG,
			SoundSource.HOSTILE, 1.0F, 0.6F + croc.getRandom().nextFloat() * 0.2F);
		if (wet) {
			level.sendParticles(ParticleTypes.SPLASH, croc.getX(), croc.getY() + croc.getBbHeight(), croc.getZ(), 24,
				croc.getBbWidth() * 0.5, 0.2, croc.getBbWidth() * 0.5, 0.3);
		}
	}

	/** What it holds, at its jaws: before its snout, at its own height (under the water with it). */
	private static void place(Mob croc, Entity prey) {
		float yaw = croc.getYRot() * Mth.DEG_TO_RAD;
		double ahead = (croc.getBbWidth() + prey.getBbWidth()) * 0.5;
		double x = croc.getX() - Mth.sin(yaw) * ahead, z = croc.getZ() + Mth.cos(yaw) * ahead, y = croc.getY();
		Vec3 at = new Vec3(x, y, z);
		if (!croc.level().noCollision(prey, prey.getDimensions(prey.getPose()).makeBoundingBox(at))) {
			// (against a wall: held where it is)
			x = prey.getX();
			z = prey.getZ();
		}
		prey.setDeltaMovement(Vec3.ZERO);
		prey.fallDistance = 0.0F;
		if (prey instanceof ServerPlayer player) {
			player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
		} else {
			prey.setPos(x, y, z);
		}
	}

	/** The nearest water it can drag to, within {@link #WATER_SEARCH}; null if none. */
	private static @Nullable BlockPos water(ServerLevel level, BlockPos from) {
		BlockPos best = null;
		double bestSq = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(from.offset(-WATER_SEARCH, -4, -WATER_SEARCH), from.offset(WATER_SEARCH, 2, WATER_SEARCH))) {
			if (level.getFluidState(pos).is(FluidTags.WATER) && level.getFluidState(pos.below()).is(FluidTags.WATER)) {
				double d = pos.distSqr(from);
				if (d < bestSq) {
					bestSq = d;
					best = pos.immutable();
				}
			}
		}
		return best;
	}

	/** Kitchen's body lying nearest {@code at}, within {@link #BODY_NEAR}; null if none. */
	private static @Nullable Entity bodyNear(ServerLevel level, Vec3 at) {
		Entity best = null;
		double bestSq = BODY_NEAR * BODY_NEAR;
		for (Entity e : level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(at, at).inflate(BODY_NEAR),
			e -> CORPSE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType())) && !held(e))) {
			double d = e.distanceToSqr(at);
			if (d < bestSq) {
				bestSq = d;
				best = e;
			}
		}
		return best;
	}
}
