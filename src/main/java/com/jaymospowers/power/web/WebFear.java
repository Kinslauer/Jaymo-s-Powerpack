package com.jaymospowers.power.web;

import com.jaymospowers.compat.KingdomsCompat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Fear of the strung up: a humanoid hostile (zombie, skeleton, illager, piglin, witch) that comes within sight of a
 * comrade strung up or cocooned by a web slinger loses its nerve, once for each such sight. What it does comes from its
 * temperament when Jaymo's Kingdoms is loaded ({@link KingdomsCompat#temperament}): the timid and gloomy flee, the
 * brave, hotheaded, proud and spiteful keep coming, the rest bunch up. Without Kingdoms, or for a creature with none,
 * a per-mob roll decides: some flee, some bunch up, some keep coming. Players see the foes break and run.
 */
public final class WebFear {
	// ================================================================================================================
	// Tuning
	/** How far (blocks) a watcher sees a comrade, how often (ticks) the watch is made, and how long a comrade is feared. */
	static final double SIGHT = 18.0;
	static final int EVERY = 10, COMRADE_TICKS = 1200;
	/** The roll with no temperament (out of 100): below FLEE_PCT flees, below BUNCH_PCT bunches up, else keeps coming. */
	static final int FLEE_PCT = 40, BUNCH_PCT = 70;
	/** How long a fright lasts (ticks), and how much of it a bunching mob huddles. */
	static final int FRIGHT_TICKS = 160, BUNCH_TICKS = 100;
	/** A fleeing mob's pace, and how far it runs from its fright (blocks). */
	static final double FLEE_SPEED = 1.35;
	static final int FLEE_RANGE = 14;
	// ================================================================================================================

	private enum Nerve {
		FLEE, BUNCH, HOLD
	}

	private record Comrade(UUID id, ResourceKey<Level> dimension, long until) {
	}

	private record Shaken(Nerve nerve, long until, UUID comrade) {
	}

	private static final List<Comrade> COMRADES = new ArrayList<>();
	/** Per watcher: what its fright is and until when. */
	private static final Map<UUID, Shaken> SHAKEN = new HashMap<>();
	/** Per comrade: the watchers it has already shaken (a watcher is shaken once by each). */
	private static final Map<UUID, Set<UUID>> SEEN = new HashMap<>();

	private WebFear() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WebFear::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			COMRADES.clear();
			SHAKEN.clear();
			SEEN.clear();
		});
	}

	/** {@code comrade} was strung up or cocooned: foes that see it will lose their nerve. */
	public static void strungUp(ServerLevel level, Entity comrade) {
		if (!(comrade instanceof Enemy)) {
			return;
		}
		COMRADES.removeIf(c -> c.id().equals(comrade.getUUID()));
		SEEN.remove(comrade.getUUID());
		COMRADES.add(new Comrade(comrade.getUUID(), level.dimension(), level.getGameTime() + COMRADE_TICKS));
	}

	/** {@code mob} loses its nerve and runs from {@code source} until game time {@code until} (the big suit's roar). */
	public static void scare(Mob mob, Entity source, long until) {
		SHAKEN.put(mob.getUUID(), new Shaken(Nerve.FLEE, until, source.getUUID()));
	}

	private static boolean humanoid(Mob mob) {
		return mob instanceof Zombie || mob instanceof AbstractSkeleton || mob instanceof AbstractIllager || mob instanceof AbstractPiglin
			|| mob instanceof Witch;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Comrade> it = COMRADES.iterator(); it.hasNext(); ) {
			Comrade c = it.next();
			ServerLevel level = server.getLevel(c.dimension());
			Entity e = level == null ? null : level.getEntity(c.id());
			if (e == null || !e.isAlive() || level.getGameTime() >= c.until()) {
				it.remove();
				SEEN.remove(c.id());
				continue;
			}
			if (level.getGameTime() % EVERY != 0) {
				continue;
			}
			for (Mob mob : level.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(SIGHT),
				m -> m != e && m.isAlive() && m instanceof Enemy && humanoid(m) && !WebHanging.webbedUp(m) && !m.hasAttached(WebPower.HITCH))) {
				if (mob.distanceTo(e) > SIGHT || !mob.hasLineOfSight(e)) {
					continue;
				}
				Set<UUID> seen = SEEN.computeIfAbsent(c.id(), k -> new HashSet<>());
				if (seen.add(mob.getUUID())) {
					SHAKEN.put(mob.getUUID(), new Shaken(roll(mob, e), level.getGameTime() + FRIGHT_TICKS, c.id()));
					level.sendParticles(ParticleTypes.SPLASH, mob.getX(), mob.getEyeY() + 0.3, mob.getZ(), 6, 0.2, 0.1, 0.2, 0.0);
					level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.VILLAGER_NO, SoundSource.HOSTILE, 0.5F, 0.6F);
				}
			}
		}
		if (SHAKEN.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Shaken>> it = SHAKEN.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Shaken> entry = it.next();
			Shaken s = entry.getValue();
			Mob mob = null;
			Entity comrade = null;
			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntity(entry.getKey()) instanceof Mob m) {
					mob = m;
					comrade = level.getEntity(s.comrade());
					break;
				}
			}
			if (mob == null || !mob.isAlive() || mob.level().getGameTime() >= s.until()) {
				it.remove();
				continue;
			}
			long now = mob.level().getGameTime();
			if (now % 5 != 0 || s.nerve() == Nerve.HOLD) {
				continue;
			}
			Vec3 from = comrade != null ? comrade.position() : mob.position();
			if (s.nerve() == Nerve.FLEE) {
				mob.setTarget(null);
				if (mob instanceof PathfinderMob p && p.getNavigation().isDone()) {
					Vec3 to = DefaultRandomPos.getPosAway(p, FLEE_RANGE, 6, from);
					if (to != null) {
						p.getNavigation().moveTo(to.x, to.y, to.z, FLEE_SPEED);
					}
				}
			} else {
				final Mob self = mob;
				if (now >= s.until() - (FRIGHT_TICKS - BUNCH_TICKS)) {
					it.remove();
					continue;
				}
				mob.setTarget(null);
				Vec3 sum = Vec3.ZERO;
				int n = 0;
				for (Mob other : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(10.0), m -> m != self && m.isAlive() && m instanceof Enemy)) {
					sum = sum.add(other.position());
					n++;
				}
				if (n > 0 && mob.distanceToSqr(sum.scale(1.0 / n)) > 6.25) {
					Vec3 c = sum.scale(1.0 / n);
					mob.getNavigation().moveTo(c.x, c.y, c.z, 1.0);
				} else {
					mob.getNavigation().stop();
				}
			}
		}
	}

	private static Nerve roll(Mob mob, Entity comrade) {
		String temper = KingdomsCompat.temperament(mob);
		if (temper != null) {
			return switch (temper) {
				case "timid", "gloomy" -> Nerve.FLEE;
				case "brave", "hotheaded", "proud", "spiteful" -> Nerve.HOLD;
				default -> Nerve.BUNCH;
			};
		}
		int r = Math.floorMod(mob.getUUID().hashCode() * 31 + comrade.getUUID().hashCode(), 100);
		return r < FLEE_PCT ? Nerve.FLEE : r < BUNCH_PCT ? Nerve.BUNCH : Nerve.HOLD;
	}
}
