package com.jaymoskingdoms;

import com.jaymoskingdoms.Temperaments.Temperament;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.622, Rivals patch 7) Brutal kills scare the rest (the Shadow of War idea; ideas only). A foe finished in front of its
 * fellows (downed and executed, or its head off) frightens those near who saw it and are weaker than the one who did it: they
 * break off and run for a while. The timid run first and always, the gloomy nearly always, the brave never; the proud and the
 * hotheaded seldom; the rest about half the time ({@link Temperaments}). The undead and the mindless don't scare; nor do
 * captains, Banes or anything big ({@link #STRONG}).
 */
public final class Dread {
	// ================================================================================================================
	// Tuning
	/** Those within this of the kill who can see the killer may take fright. */
	static final double RANGE = 16.0;
	/** Only those with no more than this much health (and no more than the killer's) scare. */
	static final float STRONG = 60.0F;
	/** The chance each takes fright, by temperament; for those with none (illagers have them; monsters don't). */
	static final float TIMID = 1.0F, GLOOMY = 0.85F, BRAVE = 0.0F, BOLD = 0.25F, OTHER = 0.5F, NO_TEMPER = 0.5F;
	/** How long they run (ticks), and how far each leg. */
	static final int RUN_TICKS = 140;
	static final double RUN_LEG = 12.0;
	static final int LINES = 4;
	// ================================================================================================================

	private record Fright(Vec3 from, long until) {
	}

	private static final Map<PathfinderMob, Fright> FRIGHTS = new java.util.WeakHashMap<>();

	private Dread() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && victim.level() instanceof ServerLevel level && !(victim instanceof ServerPlayer)
				&& (Downed.isDowned(victim) || victim.entityTags().contains(Captains.MISSING_TAG + "head"))) {
				witnessed(level, victim, killer);
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (!FRIGHTS.isEmpty() && level.getGameTime() % 5L == 0L) {
				run(level);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FRIGHTS.clear());
	}

	/** {@code killer} finished {@code victim}: those near who saw it and are weaker may run. */
	private static void witnessed(ServerLevel level, LivingEntity victim, ServerPlayer killer) {
		Vec3 at = victim.position();
		long now = level.getGameTime();
		boolean spoke = false;
		List<Mob> near = level.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(RANGE), m -> m != victim && m.isAlive() && scares(m, killer));
		for (Mob m : near) {
			if (!(m instanceof PathfinderMob pm) || !m.getSensing().hasLineOfSight(killer) || level.getRandom().nextFloat() >= chance(m)) {
				continue;
			}
			FRIGHTS.put(pm, new Fright(at, now + RUN_TICKS));
			// (and the fright proper: its feet won't carry it back toward the killer)
			Fear.frighten(pm, killer, RUN_TICKS);
			pm.setTarget(null);
			// (Powers' Crowds' tag: no blows, no closing in, while it runs)
			pm.addTag(MobCombat.WAITING_TAG);
			pm.getNavigation().stop();
			if (!spoke && Temperaments.humanoid(m)) {
				spoke = true;
				Speech.say(level, m, Component.translatable("talk.jaymoskingdoms.dread." + level.getRandom().nextInt(LINES)), Speech.YELLOW, Captains.SAY_RANGE);
			} else {
				m.playAmbientSound();
			}
		}
	}

	/** One who can take fright at {@code killer}'s kill: a foe of theirs, living, and no stronger than them. */
	private static boolean scares(Mob m, ServerPlayer killer) {
		if (m.is(EntityTypeTags.UNDEAD) || Captains.is(m)) {
			return false;
		}
		boolean foe = m.getTarget() == killer || m instanceof Enemy && m.distanceTo(killer) <= RANGE;
		return foe && m.getMaxHealth() <= STRONG && m.getMaxHealth() <= killer.getMaxHealth() * 1.5F;
	}

	private static float chance(Mob m) {
		Temperament t = Temperaments.of(m);
		if (t == null) {
			return NO_TEMPER;
		}
		return switch (t) {
			case TIMID -> TIMID;
			case GLOOMY -> GLOOMY;
			case BRAVE -> BRAVE;
			case PROUD, HOTHEADED -> BOLD;
			default -> OTHER;
		};
	}

	/** Every few ticks: those frightened keep running from where it happened, till it wears off. */
	private static void run(ServerLevel level) {
		long now = level.getGameTime();
		for (Map.Entry<PathfinderMob, Fright> e : new HashMap<>(FRIGHTS).entrySet()) {
			PathfinderMob m = e.getKey();
			if (m.level() != level) {
				continue;
			}
			Fright f = e.getValue();
			if (!m.isAlive() || now >= f.until() || Downed.isDowned(m)) {
				FRIGHTS.remove(m);
				m.removeTag(MobCombat.WAITING_TAG);
				continue;
			}
			m.setTarget(null);
			m.addTag(MobCombat.WAITING_TAG);
			if (m.getNavigation().isDone() || now % 20L == 0L) {
				Vec3 away = m.position().subtract(f.from()).multiply(1.0, 0.0, 1.0);
				if (away.lengthSqr() < 0.01) {
					double angle = m.getRandom().nextDouble() * Math.PI * 2.0;
					away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
				}
				Vec3 to = m.position().add(away.normalize().scale(RUN_LEG));
				m.getNavigation().moveTo(to.x, to.y, to.z, 1.4);
			}
		}
	}

	/** Whether {@code m} is running in fright just now. */
	static boolean frightened(Mob m) {
		return m instanceof PathfinderMob pm && FRIGHTS.containsKey(pm);
	}
}
