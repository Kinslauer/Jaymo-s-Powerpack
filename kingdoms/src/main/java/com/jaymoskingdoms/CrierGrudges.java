package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * Those who don't like what the {@link TownCrier} cries. Andy (2026-11-26): "npcs that dont like what they are saying may
 * attack or hire a hit on them".
 *
 * <p>When the crier has cried a piece of news, the townsfolk standing by to hear it may take it badly: the kin of the one
 * sentenced (MCA's family tree) at any punishment, the harder the more, and a crabby or grumpy one at a pardon (too
 * soft). At most one of them speaks up each time, shouting at the crier. Some go further: they walk up and set about him
 * with their fists ({@link #ASSAULT_TICKS} at most, until they're badly hurt, or a guard has them: it's an assault, and
 * the guards are called, {@link Arrests#arrest}); or, a kinsman only, they pay a blade to come for him
 * ({@link Assassins#onCrier}). It all happens where it's cried, so only with a player by to see it.
 */
final class CrierGrudges {
	/** Of the kin who hear a sentence cried, the chance one speaks up, by sentence (pardon, flog, jail, exile, execute; a ranger's fine, his taking). */
	static final Map<String, Float> KIN_CHANCE = Map.of("flog", 0.5F, "jail", 0.6F, "exile", 0.7F, "execute", 0.9F, "fined", 0.3F, "taken", 0.6F);
	/** A crabby or grumpy one who hears a pardon cried speaks up this often. */
	static final float SOFT_CHANCE = 0.3F;
	/** Of those who speak up: the chance they go for the crier, and (kin, at an exile or a death) the chance they hire a blade instead. */
	static final float KIN_ATTACK = 0.35F, SOFT_ATTACK = 0.1F, KIN_HIRE_GRAVE = 0.35F, KIN_HIRE = 0.1F;
	/** The grave sentences, for {@link #KIN_HIRE_GRAVE}. */
	static final Set<String> GRAVE = Set.of("exile", "execute", "taken");
	/** An assault: how long it may go on (ticks), a punch's reach, sting and how often, the walk, and when the attacker gives up hurt. */
	static final int ASSAULT_TICKS = 20 * 20, PUNCH_TICKS = 20;
	static final double REACH = 2.0;
	static final float PUNCH = 1.5F, WALK = 0.8F, GIVE_UP_SHARE = 0.3F;
	/** How far their shouts carry. */
	static final double SHOUT_RANGE = 20.0;
	/** Lines each (lang {@code crier.jaymoskingdoms.grudge.<key>.<n>}). */
	static final int LINES = 3;

	/** Someone set on the crier: who, the realm, when it ends, when they may punch next, and whether the guards were called. */
	private static final class Assault {
		final UUID crier;
		final int realm;
		final long until;
		long nextPunch;
		boolean called;

		Assault(UUID crier, int realm, long until) {
			this.crier = crier;
			this.realm = realm;
			this.until = until;
		}
	}

	/** Attacker → their assault on a crier. */
	private static final Map<UUID, Assault> ASSAULTS = new HashMap<>();

	private CrierGrudges() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CrierGrudges::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ASSAULTS.clear());
	}

	/** {@code crier} of {@code f} has just cried what became of {@code about} ({@code outcome}): anyone by who minds may act on it. */
	static void heard(ServerLevel level, Faction f, Villager crier, UUID about, String outcome) {
		RandomSource random = level.getRandom();
		boolean pardon = "pardon".equals(outcome);
		Float kinChance = KIN_CHANCE.get(outcome);
		Set<Villager> guards = new HashSet<>();
		for (Worker w : Workers.near(level, crier.getBoundingBox().inflate(TownCrier.EARSHOT), w -> w.role() == Folk.GUARD)) {
			if (w.mob() instanceof Villager v) {
				guards.add(v);
			}
		}
		Villager angry = null;
		boolean kin = false;
		double best = Double.MAX_VALUE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, crier.getBoundingBox().inflate(TownCrier.EARSHOT), v -> v != crier && v.isAlive()
			&& !v.isBaby() && !v.isSleeping() && !Downed.isDowned(v) && !guards.contains(v) && !Rulers.is(v) && !Arrests.held(v) && !Arrests.busy(v)
			&& !ASSAULTS.containsKey(v.getUUID()) && !v.getUUID().equals(about))) {
			boolean theirs = !pardon && kinChance != null && McaCompat.kinOf(v, about) != McaCompat.STRANGERS;
			String temper = McaCompat.personality(v);
			boolean soft = pardon && ("crabby".equals(temper) || "grumpy".equals(temper));
			if (!theirs && !soft) {
				continue;
			}
			// their own kin first; then the nearest
			double d = v.distanceToSqr(crier) - (theirs ? 1.0E6 : 0.0);
			if (d < best) {
				best = d;
				angry = v;
				kin = theirs;
			}
		}
		if (angry == null || random.nextFloat() >= (kin ? kinChance : SOFT_CHANCE)) {
			return;
		}
		angry.getLookControl().setLookAt(crier, 30.0F, 30.0F);
		say(level, angry, kin ? "kin" : "soft", Speech.RED);
		float roll = random.nextFloat();
		float attack = kin ? KIN_ATTACK : SOFT_ATTACK;
		if (roll < attack) {
			ASSAULTS.put(angry.getUUID(), new Assault(crier.getUUID(), f.id(), level.getGameTime() + ASSAULT_TICKS));
			say(level, angry, "attack", Speech.RED);
		} else if (kin && roll < attack + (GRAVE.contains(outcome) ? KIN_HIRE_GRAVE : KIN_HIRE)) {
			// a word with someone in a hood, after: the crier will hear of it only when the blade comes (and the town may whisper)
			Assassins.onCrier(level, crier);
		}
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (ASSAULTS.isEmpty()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % 5 != 0) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Assault>> it = ASSAULTS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Assault> e = it.next();
			Assault a = e.getValue();
			if (!step(level, e.getKey(), a, now)) {
				if (level.getEntity(e.getKey()) instanceof Villager v) {
					v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				}
				it.remove();
			}
		}
	}

	/** One beat of an assault; false when it's over. */
	private static boolean step(ServerLevel level, UUID who, Assault a, long now) {
		if (!(level.getEntity(who) instanceof Villager v) || !v.isAlive() || Downed.isDowned(v) || Arrests.caught(v)
			|| !(level.getEntity(a.crier) instanceof Villager crier) || !crier.isAlive() || Downed.isDowned(crier) || now > a.until) {
			return false;
		}
		if (v.getHealth() < v.getMaxHealth() * GIVE_UP_SHARE) {
			say(level, v, "beaten", Speech.WHITE);
			return false;
		}
		// standing their ground: no panicking from the blows they take back
		v.getBrain().eraseMemory(MemoryModuleType.HURT_BY);
		v.getBrain().eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
		v.getLookControl().setLookAt(crier, 30.0F, 30.0F);
		if (v.distanceTo(crier) > REACH) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(crier, true), WALK, 1);
			return true;
		}
		if (now < a.nextPunch || !v.hasLineOfSight(crier)) {
			return true;
		}
		a.nextPunch = now + PUNCH_TICKS;
		v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		crier.hurtServer(level, level.damageSources().mobAttack(v), PUNCH);
		level.playSound(null, crier.getX(), crier.getY(), crier.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.NEUTRAL, 1.0F, 1.0F);
		if (!a.called) {
			// the first blow: the crier cries for the watch, and they come for the one who struck him
			a.called = true;
			say(level, crier, "help", Speech.RED);
			Faction f = Factions.get(level.getServer(), a.realm);
			if (f != null) {
				Arrests.arrest(level, v, f, "assault");
			}
		}
		return true;
	}

	private static void say(ServerLevel level, Villager who, String key, int color) {
		Speech.say(level, who, Component.translatable("crier.jaymoskingdoms.grudge." + key + "." + level.getRandom().nextInt(LINES)), color, SHOUT_RANGE);
	}
}
