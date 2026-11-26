package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Skittish herds (after Better With Mods' improved fleeing). Hurt a herd animal (the {@code jaymoskingdoms:skittish}
 * entity tag: cows, sheep, pigs, goats, horses, llamas, rabbits, chickens) and the others of its kind nearby that see it
 * bolt from the attacker for a few seconds. A player sprinting or breaking blocks near one spooks it too; sneaking never
 * does, and the Stealth pillar's Soft Step perk ({@link Skills}) lets you come closer. A leashed, tamed or named animal
 * doesn't spook (it still panics when hit). Penned animals aren't told apart: a fence holds them anyway.
 * <p>
 * (0.7.560, Andy: "passive animals should run when someone is killed near them") A killing scares every herd animal
 * near enough that sees it (or near enough to hear it, out of sight): someone dying, going down, or a blow landing on
 * the downed. They run from the killer, or from the body if nothing killed it. Only a ridden or tamed animal keeps its
 * head; a leashed one strains at the rope.
 * <p>
 * Known hunters (after Scarify, CC0: mobs fear players as sheep fear wolves; Andy: "Prey could learn to run from a known
 * hunter YES"). A herd animal that sees (or hears, close by) one of its own kind killed by a player or a person
 * ({@link #hunter}) remembers who did it ({@link #KNOWN_HUNTERS}, kept on the animal) and from then on runs from them on
 * sight ({@link #HUNTER_SIGHT}, half as far while they sneak), and its kin run with it. Each killing it sees makes the
 * memory last longer ({@link #MEMORY_TICKS}, at most {@link #MEMORY_MOST}); left alone, it fades. A player who never
 * hunts them is never feared, and a ridden, tamed or leashed animal keeps its head.
 */
public final class Herds {
	/** The herd animals. */
	static final TagKey<EntityType<?>> SKITTISH = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("skittish"));
	/** How far a hurt animal's kin panic with it; how close a sprinting or digging player spooks one (blocks). */
	static final double HERD_PANIC_RANGE = 10.0, SPOOK_RANGE = 8.0;
	/** Soft Step: per rank, the spook range shrinks this much; it never shrinks below the minimum. */
	static final double SOFT_STEP_SHRINK = 2.0, SPOOK_MIN = 2.0;
	/** How long they run (ticks); how often a run is re-aimed and sprinters are checked (ticks); how fast and how far they go. */
	static final int FLEE_TICKS = 80, EVERY = 10, RUN = 14, RUN_Y = 5;
	static final double FLEE_SPEED = 1.8;
	/** How far a killing scares herd animals that see it; how close they hear it out of sight (blocks). */
	static final double KILL_SEEN = 16.0, KILL_HEARD = 6.0;

	/** How far a herd animal sees a hunter it knows (blocks; half that while they sneak). */
	static final double HUNTER_SIGHT = 20.0;
	/** How much longer each killing it sees makes its memory of the killer (ticks: three days), and the longest it lasts (ticks: ten days). */
	static final long MEMORY_TICKS = 72000L, MEMORY_MOST = 240000L;

	/** Hunter (UUID) → game time the animal remembers them until. Saved on the animal. */
	static final AttachmentType<Map<String, Long>> KNOWN_HUNTERS = AttachmentRegistry.create(JaymosKingdoms.id("known_hunters"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

	/** People (not players) some animal fears, until when: looked about for as players are. */
	private static final Map<LivingEntity, Long> PEOPLE_HUNTERS = new WeakHashMap<>();

	/** What it runs from: a creature, or (no creature) a place, where a disaster is coming (Signs). */
	private record Flight(@Nullable Entity from, Vec3 at, long until) {
		Vec3 away() {
			return from != null ? from.position() : at;
		}
	}

	private static final Map<PathfinderMob, Flight> FLIGHTS = new HashMap<>();

	private Herds() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Herds::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FLIGHTS.clear();
			PEOPLE_HUNTERS.clear();
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && entity instanceof PathfinderMob hurt && skittish(hurt) && source.getEntity() instanceof LivingEntity attacker
				&& attacker != hurt && hurt.level() instanceof ServerLevel level) {
				panic(level, hurt, attacker);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			// (a blow that put someone down, or landed on someone already down)
			if (taken > 0.0F && !skittish(entity) && Downed.isDowned(entity) && entity.level() instanceof ServerLevel level) {
				killing(level, entity, source.getEntity());
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!skittish(entity) && entity.level() instanceof ServerLevel level) {
				killing(level, entity, source.getEntity());
			}
			if (skittish(entity) && source.getEntity() instanceof LivingEntity killer && hunter(killer) && entity.level() instanceof ServerLevel level) {
				hunted(level, entity, killer);
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

	/** {@code body} was killed or downed by {@code killer} (or by nothing): herd animals near enough run from it. */
	private static void killing(ServerLevel level, LivingEntity body, @Nullable Entity killer) {
		Entity from = killer != null && killer != body && killer.isAlive() ? killer : body;
		for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, body.getBoundingBox().inflate(KILL_SEEN),
			m -> m != body && m.isAlive() && skittish(m) && !FLIGHTS.containsKey(m) && !m.isVehicle() && !owned(m))) {
			double d = m.distanceToSqr(body);
			if (d <= KILL_HEARD * KILL_HEARD || d <= KILL_SEEN * KILL_SEEN && m.hasLineOfSight(body)) {
				flee(level, m, from);
			}
		}
	}

	/** Who a herd learns to fear: a player, or a person (a villager, our folk, an illager). */
	static boolean hunter(Entity e) {
		return e instanceof Player p && !p.isCreative() && !p.isSpectator() || Temperaments.humanoid(e);
	}

	/** {@code body}, a herd animal, was killed by {@code killer}: its kind near enough remember them, and run. */
	private static void hunted(ServerLevel level, LivingEntity body, LivingEntity killer) {
		long now = level.getGameTime();
		String who = killer.getUUID().toString();
		boolean any = false;
		for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, body.getBoundingBox().inflate(KILL_SEEN),
			m -> m != body && m.isAlive() && m.getType() == body.getType() && !owned(m))) {
			double d = m.distanceToSqr(body);
			if (d <= KILL_HEARD * KILL_HEARD || d <= KILL_SEEN * KILL_SEEN && m.hasLineOfSight(body)) {
				Map<String, Long> known = new HashMap<>(m.getAttachedOrElse(KNOWN_HUNTERS, Map.of()));
				known.values().removeIf(until -> until <= now);
				known.merge(who, now + MEMORY_TICKS, (was, fresh) -> Math.min(now + MEMORY_MOST, was + MEMORY_TICKS));
				m.setAttached(KNOWN_HUNTERS, known);
				any = true;
				if (!FLIGHTS.containsKey(m) && !m.isVehicle()) {
					flee(level, m, killer);
				}
			}
		}
		if (any && !(killer instanceof Player)) {
			PEOPLE_HUNTERS.merge(killer, now + MEMORY_MOST, Math::max);
		}
	}

	/** Whether {@code m} remembers {@code hunter} as one who kills its kind. */
	static boolean knows(PathfinderMob m, Entity hunter) {
		Map<String, Long> known = m.getAttached(KNOWN_HUNTERS);
		if (known == null) {
			return false;
		}
		Long until = known.get(hunter.getUUID().toString());
		return until != null && until > m.level().getGameTime();
	}

	/** Herd animals that know {@code hunter} and see it run from it, and their kin with them. */
	private static void watchFor(ServerLevel level, LivingEntity hunter) {
		double r = hunter.isShiftKeyDown() ? HUNTER_SIGHT / 2.0 : HUNTER_SIGHT;
		for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, hunter.getBoundingBox().inflate(r),
			m -> m.isAlive() && skittish(m) && m.hasAttached(KNOWN_HUNTERS) && !FLIGHTS.containsKey(m) && !m.isVehicle() && !m.isLeashed() && !owned(m))) {
			if (m.distanceToSqr(hunter) <= r * r && knows(m, hunter) && m.hasLineOfSight(hunter)) {
				panic(level, m, hunter);
			}
		}
	}

	/** Someone's animal (tamed), which trusts its keeper more than its fear. */
	private static boolean owned(PathfinderMob m) {
		return m instanceof AbstractHorse h && h.isTamed() || m instanceof OwnableEntity o && o.getOwnerReference() != null;
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
		FLIGHTS.put(m, new Flight(from, from.position(), level.getGameTime() + FLEE_TICKS));
		run(m, from.position());
	}

	/**
	 * (Medieval picks, nd-signs) Animals know first: every herd animal within {@code range} of {@code near} runs from
	 * {@code from} (where a disaster is coming) for {@code ticks}. Only a ridden or tamed animal keeps its head.
	 */
	public static void dread(ServerLevel level, Vec3 near, Vec3 from, double range, int ticks) {
		for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, new net.minecraft.world.phys.AABB(near, near).inflate(range),
			m -> m.isAlive() && skittish(m) && !FLIGHTS.containsKey(m) && !m.isVehicle() && !owned(m))) {
			FLIGHTS.put(m, new Flight(null, from, level.getGameTime() + ticks));
			run(m, from);
		}
	}

	private static void tick(MinecraftServer server) {
		long ticks = server.getTickCount();
		if (ticks % EVERY == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.isSprinting()) {
					spook(p);
				}
				if (!p.isSpectator() && !p.isCreative() && p.level() instanceof ServerLevel level) {
					watchFor(level, p);
				}
			}
			PEOPLE_HUNTERS.entrySet().removeIf(e -> !e.getKey().isAlive() || e.getValue() <= e.getKey().level().getGameTime());
			PEOPLE_HUNTERS.keySet().forEach(h -> {
				if (h.level() instanceof ServerLevel level) {
					watchFor(level, h);
				}
			});
		}
		Iterator<Map.Entry<PathfinderMob, Flight>> it = FLIGHTS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<PathfinderMob, Flight> e = it.next();
			PathfinderMob m = e.getKey();
			if (!m.isAlive() || m.isRemoved() || m.level().getGameTime() > e.getValue().until()) {
				it.remove();
			} else if (ticks % EVERY == 0 || m.getNavigation().isDone()) {
				run(m, e.getValue().away());
			}
		}
	}

	/** It heads for a spot away from {@code from}: through its brain if it has one that walks, else its navigation. */
	private static void run(PathfinderMob m, Vec3 from) {
		Vec3 to = DefaultRandomPos.getPosAway(m, RUN, RUN_Y, from);
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
