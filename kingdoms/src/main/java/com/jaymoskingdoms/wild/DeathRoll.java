package com.jaymoskingdoms.wild;

import java.util.Map;
import java.util.Set;
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
 * alligator ({@link #JAWS}) that bites something no bigger than {@link #MOST_PREY} times itself keeps it in its jaws: it
 * thrashes side to side ({@link #turn}: the other mods' crocodiles can't be drawn rolling over), a violent turn every
 * {@link #ROLL_EVERY} ticks, each one hurting the prey ({@link #ROLL_DAMAGE}), splashing in
 * water. After {@link #ROLLS} turns it drags the prey to the nearest water ({@link #WATER_SEARCH}), and there it holds it
 * under and rolls on. If the prey dies in its jaws, it drags the body into the water instead and lets it go there, to
 * eat it (Jaymo's Kitchen's scavengers: crocodiles and alligators are in its {@code scavengers} tag). Hurting it hard
 * enough ({@link #BREAK_FREE}) makes it let go, as stabbing at a real one's eyes does.
 */
public final class DeathRoll {
	/** Cube Animals' crocodile and Naturalist's alligator. */
	static final Set<Identifier> JAWS = Set.of(Identifier.fromNamespaceAndPath("cubeanimals", "crocodile"),
		Identifier.fromNamespaceAndPath("naturalist", "alligator"));
	/** Jaymo's Kitchen's body. */
	static final Identifier CORPSE = Identifier.fromNamespaceAndPath("jaymoskitchen", "corpse");
	/** The biggest prey it takes in its jaws, as a share of its own bulk (width x width x height): a horse, an ox (Andy: "go after bigger prey"). */
	static final double MOST_PREY = 4.0;
	/** Ticks between violent turns, turns on land before it drags, harm of each turn. */
	static final int ROLL_EVERY = 12, ROLLS = 4;
	static final float ROLL_DAMAGE = 3.0F;
	/** In the water it turns this much slower (it holds the prey under between). */
	static final int WATER_ROLL_EVERY = 24;
	/** How far each thrash wrenches its head and body to one side of where it faced at the bite (degrees, both ways). */
	static final float THRASH = 55.0F, THRASH_JITTER = 20.0F;
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
		/** Which way it faced at the bite, and the side it thrashes to next (1 or -1). */
		float facing;
		int side = 1;
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
				&& !entity.isPassenger() && bulk(entity) <= bulk(croc) * MOST_PREY) {
				Grip grip = new Grip(entity, croc.getHealth());
				grip.facing = croc.getYRot();
				GRIPS.put(croc, grip);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (GRIPS.isEmpty()) {
				return;
			}
			for (Map.Entry<Mob, Grip> e : GRIPS.entrySet().toArray(Map.Entry[]::new)) {
				if (!tick(e.getKey(), e.getValue())) {
					GRIPS.remove(e.getKey());
				}
			}
		});
	}

	/** Whether {@code e} is a crocodile or an alligator. */
	static boolean jaws(Entity e) {
		return JAWS.contains(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
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
					if (++grip.turns >= ROLLS) {
						grip.phase = wet ? Phase.HOLD : Phase.DRAG;
					}
				}
			}
			case DRAG -> {
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
	 * A violent turn. Andy (2026-11-25): "for the ones we cant roll, they should thrash side to side" (the other mods'
	 * crocodiles can't be drawn turning over): it wrenches its head and body hard to one side of where it faced at the bite,
	 * then to the other, and what it holds is hurt by each.
	 */
	private static void turn(ServerLevel level, Mob croc, Entity prey, Grip grip) {
		grip.side = -grip.side;
		float yaw = grip.facing + grip.side * (THRASH + croc.getRandom().nextFloat() * THRASH_JITTER);
		croc.setYRot(yaw);
		croc.setYBodyRot(yaw);
		croc.setYHeadRot(yaw);
		if (prey instanceof LivingEntity living) {
			living.hurtServer(level, level.damageSources().mobAttack(croc), ROLL_DAMAGE);
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
