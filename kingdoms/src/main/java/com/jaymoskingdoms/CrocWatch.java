package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * Andy (2026-11-25): "nothing is safe. thats why you should also see villagers more cautious if they are nearby", then
 * "they should warn others of the area, and raise the problem to guards until they hunt them". A villager who sees a grown
 * crocodile or alligator ({@code wild/DeathRoll}) within {@link #WARY} walks away from it ({@link #seen}). The first to
 * see it cries a warning ({@link #CRY_GAP} at most once per beast), and the place is marked ({@link #Danger}): for
 * {@link #DANGER_TICKS} every villager who comes within {@link #DANGER_RADIUS} of it keeps away, warned or not. The cry
 * goes to the guards: every guard within {@link #CALL_RANGE} goes after the beast and keeps on it until it's dead or
 * {@link #HUNT_TICKS} pass without a fresh sighting; each sighting sends them again.
 */
public final class CrocWatch {
	/** How near a crocodile a villager who sees it walks away (blocks), and at what pace. */
	public static final double WARY = 10.0, WARY_SPEED = 0.8;
	/** How long between warnings about the same beast (ticks), how far a warning carries to the guards (blocks). */
	static final long CRY_GAP = 20L * 30;
	static final double CALL_RANGE = 48.0;
	/** A warned place: how long it's kept clear (ticks), and how far round it (blocks). */
	static final long DANGER_TICKS = 20L * 60 * 5;
	static final double DANGER_RADIUS = 12.0;
	/** A guard on the hunt: how long without a fresh sighting before they give up (ticks), how far they chase (blocks), pace. */
	static final long HUNT_TICKS = 20L * 60 * 3;
	static final double HUNT_RANGE = 64.0;
	static final float HUNT_SPEED = 0.9F;
	/** How often the warned places and the hunts are looked at (ticks). */
	static final int STEP_EVERY = 40;
	/** Lines: a villager's warning, their call to the guards, a guard setting out. */
	static final int WARN_LINES = 6, CALL_LINES = 4, HUNT_LINES = 4;

	/** A warned place, and the beast it was warned of. */
	private record Danger(ServerLevel level, Vec3 at, UUID beast, long until) {
	}

	private static final List<Danger> DANGERS = new ArrayList<>();
	/** The guards on a hunt: the beast, and until when. */
	private static final Map<PathfinderMob, Hunt> HUNTS = new WeakHashMap<>();
	/** When each beast was last warned of (game time). */
	private static final Map<UUID, Long> CRIED = new HashMap<>();

	private record Hunt(UUID beast, long until) {
	}

	private CrocWatch() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CrocWatch::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DANGERS.clear();
			HUNTS.clear();
			CRIED.clear();
		});
	}

	/** From {@code wild/Crocodiles} (every second, for each grown one): the villagers who see {@code croc} take care. */
	public static void seen(Mob croc) {
		if (!(croc.level() instanceof ServerLevel level)) {
			return;
		}
		boolean cried = level.getGameTime() - CRIED.getOrDefault(croc.getUUID(), Long.MIN_VALUE / 2) < CRY_GAP;
		for (AbstractVillager v : level.getEntitiesOfClass(AbstractVillager.class, croc.getBoundingBox().inflate(WARY),
			v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && v.getTarget() == null && !v.isPassenger() && v.distanceToSqr(croc) < WARY * WARY
				&& v.hasLineOfSight(croc))) {
			if (Downed.isGuard(v)) {
				hunt(level, v, croc, !cried);
				cried = true;
				continue;
			}
			away(v, croc.position(), WARY);
			if (!cried) {
				warn(level, v, croc);
				cried = true;
			}
		}
	}

	/** {@code v} cries out what it saw, marks the place, and the guards are told. */
	private static void warn(ServerLevel level, AbstractVillager v, Mob croc) {
		long now = level.getGameTime();
		CRIED.put(croc.getUUID(), now);
		Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.croc_watch.warn." + level.getRandom().nextInt(WARN_LINES),
			croc.getDisplayName()), Speech.RED, 24.0);
		DANGERS.removeIf(d -> d.beast().equals(croc.getUUID()));
		DANGERS.add(new Danger(level, croc.position(), croc.getUUID(), now + DANGER_TICKS));
		boolean called = false;
		for (LivingEntity g : level.getEntitiesOfClass(LivingEntity.class, croc.getBoundingBox().inflate(CALL_RANGE),
			e -> e.isAlive() && e instanceof PathfinderMob && Downed.isGuard(e) && !Downed.isDowned(e))) {
			if (!called) {
				Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.croc_watch.guards." + level.getRandom().nextInt(CALL_LINES)),
					Speech.RED, 24.0);
				called = true;
			}
			hunt(level, (PathfinderMob) g, croc, false);
		}
	}

	/** Guard {@code g} goes after {@code croc} (or keeps at it a while longer). */
	private static void hunt(ServerLevel level, PathfinderMob g, Mob croc, boolean shout) {
		Hunt had = HUNTS.put(g, new Hunt(croc.getUUID(), level.getGameTime() + HUNT_TICKS));
		Rulers.charge(g, croc);
		if (shout || had == null && level.getRandom().nextInt(3) == 0) {
			Speech.say(level, g, Component.translatable("talk.jaymoskingdoms.croc_watch.hunt." + level.getRandom().nextInt(HUNT_LINES),
				croc.getDisplayName()), Speech.RED, 24.0);
		}
	}

	/** {@code v} walks off, away from {@code from}. */
	private static void away(PathfinderMob v, Vec3 from, double far) {
		Vec3 to = DefaultRandomPos.getPosAway(v, (int) far + 4, 4, from);
		if (to != null) {
			v.getNavigation().moveTo(to.x, to.y, to.z, WARY_SPEED);
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % STEP_EVERY != 0 || DANGERS.isEmpty() && HUNTS.isEmpty()) {
			return;
		}
		// the warned places: kept clear while the beast lives and the warning holds
		DANGERS.removeIf(d -> d.level().getGameTime() > d.until() || !(d.level().getEntity(d.beast()) instanceof LivingEntity b) || !b.isAlive());
		for (Danger d : DANGERS) {
			for (AbstractVillager v : d.level().getEntitiesOfClass(AbstractVillager.class, net.minecraft.world.phys.AABB.ofSize(d.at(),
				DANGER_RADIUS * 2, 8.0, DANGER_RADIUS * 2), v -> v.isAlive() && !Downed.isGuard(v) && v.getTarget() == null && !v.isPassenger()
					&& v.position().distanceToSqr(d.at()) < DANGER_RADIUS * DANGER_RADIUS)) {
				away(v, d.at(), DANGER_RADIUS);
			}
		}
		// the guards on the hunt: after it till it's dead, out of reach, or the warning's gone cold
		for (Map.Entry<PathfinderMob, Hunt> e : new ArrayList<>(HUNTS.entrySet())) {
			PathfinderMob g = e.getKey();
			Hunt h = e.getValue();
			if (!(g.level() instanceof ServerLevel level) || !g.isAlive() || Downed.isDowned(g) || level.getGameTime() > h.until()
				|| !(level.getEntity(h.beast()) instanceof LivingEntity beast) || !beast.isAlive() || beast.distanceTo(g) > HUNT_RANGE) {
				HUNTS.remove(g);
				continue;
			}
			Rulers.charge(g, beast);
			if (g instanceof Villager v) {
				BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(beast, true), HUNT_SPEED, 2);
			} else {
				g.getNavigation().moveTo(beast, HUNT_SPEED);
			}
		}
	}
}
