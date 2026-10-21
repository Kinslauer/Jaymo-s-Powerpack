package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.phys.Vec3;

/**
 * Skittish herds (after Better With Mods' improved fleeing). Hurt a herd animal (the {@code jaymoskingdoms:skittish}
 * entity tag: cows, sheep, pigs, goats, horses, llamas, rabbits, chickens) and the others of its kind nearby that see it
 * bolt from the attacker for a few seconds. A player sprinting or breaking blocks near one spooks it too; sneaking never
 * does, and the Stealth pillar's Soft Step perk ({@link Skills}) lets you come closer. A leashed, tamed or named animal
 * doesn't spook (it still panics when hit). Penned animals aren't told apart: a fence holds them anyway.
 */
final class Herds {
	/** The herd animals. */
	static final TagKey<EntityType<?>> SKITTISH = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("skittish"));
	/** How far a hurt animal's kin panic with it; how close a sprinting or digging player spooks one (blocks). */
	static final double HERD_PANIC_RANGE = 10.0, SPOOK_RANGE = 8.0;
	/** Soft Step: per rank, the spook range shrinks this much; it never shrinks below the minimum. */
	static final double SOFT_STEP_SHRINK = 2.0, SPOOK_MIN = 2.0;
	/** How long they run (ticks); how often a run is re-aimed and sprinters are checked (ticks); how fast and how far they go. */
	static final int FLEE_TICKS = 80, EVERY = 10, RUN = 14, RUN_Y = 5;
	static final double FLEE_SPEED = 1.8;

	private record Flight(Entity from, long until) {
	}

	private static final Map<PathfinderMob, Flight> FLIGHTS = new HashMap<>();

	private Herds() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Herds::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FLIGHTS.clear());
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && entity instanceof PathfinderMob hurt && skittish(hurt) && source.getEntity() instanceof LivingEntity attacker
				&& attacker != hurt && hurt.level() instanceof ServerLevel level) {
				panic(level, hurt, attacker);
			}
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (player instanceof ServerPlayer sp && !state.canBeReplaced()) {
				spook(sp);
			}
		});
	}

	static boolean skittish(Entity e) {
		return e.getType().builtInRegistryHolder().is(SKITTISH);
	}

	/** It isn't spooked by noise: leashed, someone's (tamed), or named. */
	static boolean calm(PathfinderMob m) {
		return m.isLeashed() || m.hasCustomName() || m instanceof AbstractHorse h && h.isTamed()
			|| m instanceof OwnableEntity o && o.getOwnerReference() != null;
	}

	/** {@code hurt} was hit by {@code attacker}: it and its kind nearby that see it run. */
	private static void panic(ServerLevel level, PathfinderMob hurt, LivingEntity attacker) {
		flee(level, hurt, attacker);
		for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, hurt.getBoundingBox().inflate(HERD_PANIC_RANGE),
			m -> m != hurt && m.isAlive() && m.getType() == hurt.getType() && !FLIGHTS.containsKey(m))) {
			if (m.distanceToSqr(hurt) <= HERD_PANIC_RANGE * HERD_PANIC_RANGE && m.hasLineOfSight(hurt)) {
				flee(level, m, attacker);
			}
		}
	}

	/** How close {@code player} may come before a herd animal spooks (Soft Step shrinks it). */
	static double spookRange(ServerPlayer player) {
		return Math.max(SPOOK_MIN, SPOOK_RANGE - SOFT_STEP_SHRINK * Skills.rank(player, Skills.Perk.SOFT_STEP));
	}

	/** {@code player} made a racket: herd animals near enough that see them run. Sneaking never spooks. */
	private static void spook(ServerPlayer player) {
		if (player.isShiftKeyDown() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		double r = spookRange(player);
		for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(r),
			m -> m.isAlive() && skittish(m) && !FLIGHTS.containsKey(m) && !calm(m))) {
			if (m.distanceToSqr(player) <= r * r && m.hasLineOfSight(player)) {
				flee(level, m, player);
			}
		}
	}

	private static void flee(ServerLevel level, PathfinderMob m, Entity from) {
		FLIGHTS.put(m, new Flight(from, level.getGameTime() + FLEE_TICKS));
		run(m, from);
	}

	private static void tick(MinecraftServer server) {
		long ticks = server.getTickCount();
		if (ticks % EVERY == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.isSprinting()) {
					spook(p);
				}
			}
		}
		Iterator<Map.Entry<PathfinderMob, Flight>> it = FLIGHTS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<PathfinderMob, Flight> e = it.next();
			PathfinderMob m = e.getKey();
			if (!m.isAlive() || m.isRemoved() || m.level().getGameTime() > e.getValue().until()) {
				it.remove();
			} else if (ticks % EVERY == 0 || m.getNavigation().isDone()) {
				run(m, e.getValue().from());
			}
		}
	}

	/** It heads for a spot away from {@code from}: through its brain if it has one that walks, else its navigation. */
	private static void run(PathfinderMob m, Entity from) {
		Vec3 to = DefaultRandomPos.getPosAway(m, RUN, RUN_Y, from.position());
		if (to == null) {
			return;
		}
		if (m.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
			BehaviorUtils.setWalkAndLookTargetMemories(m, BlockPos.containing(to), (float)FLEE_SPEED, 0);
		} else {
			m.getNavigation().moveTo(to.x, to.y, to.z, FLEE_SPEED);
		}
	}
}
