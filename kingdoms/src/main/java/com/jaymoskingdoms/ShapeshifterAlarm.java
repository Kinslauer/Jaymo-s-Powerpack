package com.jaymoskingdoms;

import com.jaymoskingdoms.creatures.Shapeshifter;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Whoever sees a stolen life's copy drop its face ({@link Doubles#expose}) reacts (Andy, 2026-11-13: an unmasked shapeshifter
 * must fight or flee, and those who saw it react as well). The world's people within {@link #WITNESS_RANGE} who can see it:
 * <ul>
 * <li>The one whose life it stole, if they're there, cries out that it's the thing that took them.</li>
 * <li>Guards, a realm's fighting folk and brave villagers ({@link Temperaments#fearless}) go for it with whatever they hold
 * ({@link #defend}); one of them shouts.</li>
 * <li>The rest run and hide ({@link Frights#scare}); the first cries out whose face it wore, the next calls the guards. A
 * realm's other folk run too.</li>
 * <li>Called, every guard within {@link #CALL_RANGE} comes, seen it or not.</li>
 * </ul>
 * The shapeshifter counts those coming at it when it weighs whether to fight or run ({@link Shapeshifter}). Players who see
 * it are shaken as by anything of the other side ({@link Exposure}).
 */
public final class ShapeshifterAlarm {
	/** How far the unmasking is seen (blocks, in sight), and how far a cry for the guards carries (blocks). */
	static final double WITNESS_RANGE = 16.0, CALL_RANGE = 32.0;
	/** A defender: how long it keeps after the shapeshifter (ticks), how far it chases before giving up (blocks), its pace. */
	static final int DEFEND_TICKS = 20 * 30;
	static final double CHASE_RANGE = 28.0;
	static final float VILLAGER_DEFEND_SPEED = 0.9F;
	static final double FOLK_DEFEND_SPEED = 1.3, FOLK_RUN = 14.0;
	/** A villager's blow: its reach (blocks), its damage with a weapon in hand and without, how often (ticks). */
	static final double BLOW_REACH = 2.3;
	static final float WEAPON_BLOW = 4.0F, BARE_BLOW = 1.5F;
	static final int BLOW_EVERY = 20;
	/** How often defenders are moved along (ticks); lines of each kind of cry. */
	static final int STEP_EVERY = 5;
	static final int CRY_LINES = 8, CALL_LINES = 4, FIGHT_LINES = 4, TAKEN_LINES = 3;
	/** How far the cries carry (blocks). */
	static final double SHOUT = 24.0;

	/** A defender after a shapeshifter: which, and until when, and its next blow. */
	private static final class Defense {
		final UUID foe;
		long until;
		long nextBlow;

		Defense(UUID foe, long until) {
			this.foe = foe;
			this.until = until;
		}
	}

	private static final Map<Mob, Defense> DEFENDERS = new WeakHashMap<>();

	private ShapeshifterAlarm() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ShapeshifterAlarm::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> DEFENDERS.clear());
	}

	/** {@code e} is going for {@code shapeshifter} just now. */
	public static boolean defending(LivingEntity e, LivingEntity shapeshifter) {
		Defense d = e instanceof Mob m ? DEFENDERS.get(m) : null;
		return d != null && d.foe.equals(shapeshifter.getUUID());
	}

	/**
	 * {@code s} has just dropped the face of {@code wore} ({@code by}: whoever found it out, if anyone; {@code real}: the one
	 * whose life it stole, if it's them it met): whoever sees it reacts.
	 */
	static void seen(ServerLevel level, LivingEntity s, Component wore, @Nullable LivingEntity by, @Nullable UUID real) {
		long now = level.getGameTime();
		boolean cried = false, called = false, shouted = false;
		for (LivingEntity w : level.getEntitiesOfClass(LivingEntity.class, s.getBoundingBox().inflate(WITNESS_RANGE),
			e -> e != s && e != by && witness(e) && e.hasLineOfSight(s))) {
			if (w.getUUID().equals(real) || w instanceof Villager v && v.hasAttached(Captives.CAPTIVE)) {
				// (the stolen life, still in their rescuer's care: they don't run off, they cry out)
				Speech.say(level, w, Component.translatable("talk.jaymoskingdoms.shapeshifter.taken." + level.getRandom().nextInt(TAKEN_LINES)),
					Speech.RED, SHOUT);
				called = true;
				continue;
			}
			if (defender(w)) {
				defend(level, (Mob) w, s, now, !shouted);
				shouted = true;
				continue;
			}
			if (w instanceof Villager v) {
				Frights.scare(level, v, s.position());
				if (!cried) {
					Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.shapeshifter.cry." + level.getRandom().nextInt(CRY_LINES), wore),
						Speech.RED, SHOUT);
					cried = true;
				} else if (!called) {
					Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.shapeshifter.guards." + level.getRandom().nextInt(CALL_LINES)),
						Speech.RED, SHOUT);
				}
				called = true;
			} else if (w instanceof PathfinderMob folk) {
				Vec3 away = folk.position().subtract(s.position()).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(FOLK_RUN);
				folk.getNavigation().moveTo(folk.getX() + away.x, folk.getY(), folk.getZ() + away.z, FOLK_DEFEND_SPEED);
				called = true;
			}
		}
		if (called || shouted) {
			// (the guards come, seen it or not)
			for (LivingEntity g : level.getEntitiesOfClass(LivingEntity.class, s.getBoundingBox().inflate(CALL_RANGE),
				e -> e != s && e != by && witness(e) && (Downed.isGuard(e) || e instanceof Folk f && f.combatant()))) {
				defend(level, (Mob) g, s, now, false);
			}
		}
	}

	/** One of the world's people who can take alarm: awake, grown, standing. */
	private static boolean witness(LivingEntity e) {
		return (e instanceof Villager || e instanceof Folk) && e.isAlive() && !e.isBaby() && !e.isSleeping() && !Downed.isDowned(e)
			&& !Lycans.changed(e) && !Doubles.isCopy(e);
	}

	/** Those who'd fight it: guards, a realm's fighting folk, the brave. */
	private static boolean defender(LivingEntity e) {
		return Downed.isGuard(e) || e instanceof Folk f && f.combatant() || Temperaments.fearless(e);
	}

	/** {@code m} goes for {@code s}. */
	private static void defend(ServerLevel level, Mob m, LivingEntity s, long now, boolean shout) {
		Defense d = DEFENDERS.get(m);
		if (d != null && d.foe.equals(s.getUUID())) {
			d.until = now + DEFEND_TICKS;
			return;
		}
		if (d != null) {
			standDown(m);
		}
		DEFENDERS.put(m, new Defense(s.getUUID(), now + DEFEND_TICKS));
		if (m instanceof Folk folk && folk.combatant()) {
			folk.setTarget(s);
		}
		if (shout) {
			Speech.say(level, m, Component.translatable("talk.jaymoskingdoms.shapeshifter.fight." + level.getRandom().nextInt(FIGHT_LINES)), Speech.RED, SHOUT);
		}
	}

	/** {@code m} gives up. */
	private static void standDown(Mob m) {
		Defense d = DEFENDERS.remove(m);
		if (d == null) {
			return;
		}
		if (m instanceof Folk folk && folk.getTarget() != null && folk.getTarget().getUUID().equals(d.foe)) {
			folk.setTarget(null);
		}
		if (m instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
	}

	private static void tick(MinecraftServer server) {
		if (DEFENDERS.isEmpty() || server.getTickCount() % STEP_EVERY != 0) {
			return;
		}
		for (Map.Entry<Mob, Defense> e : new ArrayList<>(DEFENDERS.entrySet())) {
			Mob m = e.getKey();
			Defense d = e.getValue();
			if (!(m.level() instanceof ServerLevel level)) {
				DEFENDERS.remove(m);
				continue;
			}
			long now = level.getGameTime();
			LivingEntity foe = level.getEntity(d.foe) instanceof LivingEntity l ? l : null;
			if (!m.isAlive() || m.isRemoved() || Downed.isDowned(m) || now > d.until || foe == null || !foe.isAlive() || foe.distanceTo(m) > CHASE_RANGE) {
				standDown(m);
				continue;
			}
			if (m instanceof Folk folk && folk.combatant()) {
				// (they fight it their own way)
				folk.setTarget(foe);
				continue;
			}
			if (m instanceof Villager v) {
				BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(foe, true), VILLAGER_DEFEND_SPEED, 1);
			} else {
				m.getNavigation().moveTo(foe, FOLK_DEFEND_SPEED);
			}
			if (m.distanceTo(foe) <= BLOW_REACH && now >= d.nextBlow && m.hasLineOfSight(foe)) {
				d.nextBlow = now + BLOW_EVERY;
				m.getLookControl().setLookAt(foe, 30.0F, 30.0F);
				m.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				boolean armed = m.getMainHandItem().has(DataComponents.WEAPON);
				foe.hurtServer(level, level.damageSources().mobAttack(m), armed ? WEAPON_BLOW : BARE_BLOW);
				level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 0.6F, 1.0F);
			}
		}
	}
}
