package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.124) Andy (2026-10-21): "can you make guards attempt to arrest before killing criminals? either give them a visible
 * audience with the king or make a jail cell building that populates passively with criminals, and executions."
 *
 * <p>A crook seen at a crime ({@link Crooks}) where a realm keeps guards isn't cut down: the nearest free guard runs them
 * down and calls on them to halt. They come quietly ({@link #SUBMIT}, less the more notorious they are) or resist, and are
 * beaten down and hauled back up. The guard then marches them to the ruler (on his throne or on his rounds; with no
 * ruler about, to the seat) for an audience everyone near can watch: the guard names the crime, the ruler pronounces
 * sentence aloud, and it's carried out on the spot. The sentence ({@link Sentence}) follows the crook's notoriety and the
 * ruler's temper ({@link Tempers}: a tyrant hangs, a noble forgives): a pardon, a flogging, exile (they walk out of the
 * realm and are gone), or execution (beheaded where they stand; with Jaymo's Kitchen the body lies headless).
 *
 * <p>A village without folk guards still sets its golems on a crook, as before.
 */
final class Arrests {
	/** How far a guard is called from; how close he must come to call a halt; how close to the ruler the audience is held. */
	static final double CALL_RANGE = 32.0, HALT_RANGE = 2.5, AUDIENCE_RANGE = 4.5;
	/** The chance a crook comes quietly: this, less this per notoriety, never under this. */
	static final float SUBMIT = 0.55F, SUBMIT_PER_NOTORIETY = 0.06F, SUBMIT_MIN = 0.15F;
	/** How long the chase, the beating, and the march may take before the guard gives up (ticks). */
	static final int CHASE_TICKS = 20 * 90, BEAT_TICKS = 20 * 60, LEAD_TICKS = 20 * 300;
	/** The guard's pace, and the prisoner's (a villager's brain). */
	static final double GUARD_SPEED = 1.1;
	static final float PRISONER_SPEED = 0.75F;
	/** At the audience: the guard speaks, then the ruler after this long, then the sentence is carried out after this (ticks). */
	static final int VERDICT_TICKS = 20 * 4, CARRY_OUT_TICKS = 20 * 3;
	/** How far words at an audience carry, and how far players are told the sentence in chat. */
	static final double AUDIENCE_SPEECH = 24.0, ANNOUNCE_RANGE = 48.0;
	/** Sentencing: each notoriety counts this much, plus up to this at random; the ruler's lean by temper (TYRANT, NOBLE, WARLORD, PARANOID, REVELER, none). */
	static final int PER_NOTORIETY = 10, CHANCE_SPREAD = 30;
	static final int[] TEMPER_LEAN = {30, -20, 15, 20, -10, 0};
	/** Sentence thresholds: a flogging from this, exile from this, execution from this. */
	static final int FLOG_FROM = 20, EXILE_FROM = 45, EXECUTE_FROM = 70;
	/** A flogging: blows, their sting, ticks between them; never below this health. */
	static final int FLOG_BLOWS = 3, FLOG_GAP = 15;
	static final float FLOG_DAMAGE = 2.0F, FLOG_SPARES = 4.0F;
	/** Exile: how far they walk, and how long before they're gone for good (ticks). */
	static final double EXILE_DISTANCE = 80.0;
	static final int EXILE_TICKS = 20 * 40;
	/** Lines each (lang {@code arrest.jaymoskingdoms.<key>.<n>}). */
	static final int LINES = 3;
	/** Jaymo's Kitchen's tag for a body with no head (and Jaymo's Powers won't down a headless one). */
	static final String HEADLESS_TAG = "jaymoskitchen.missing.head";

	enum Sentence {
		PARDON, FLOG, EXILE, EXECUTE;

		String key() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Told of every sentence carried out on a crook (for the bounty board: brought in). */
	public interface Sentenced {
		void sentenced(LivingEntity crook, int faction, String sentence);
	}

	public static final List<Sentenced> SENTENCED = new ArrayList<>();

	private enum Stage {
		CHASE, BEAT, LEAD, AUDIENCE, PUNISH, EXILE
	}

	private static final class Arrest {
		final Villager crook;
		final Folk guard;
		final int faction;
		final String crime;
		Stage stage = Stage.CHASE;
		long until, next;
		@Nullable Sentence sentence;
		int blows;
		Vec3 away = Vec3.ZERO;

		Arrest(Villager crook, Folk guard, int faction, String crime, long until) {
			this.crook = crook;
			this.guard = guard;
			this.faction = faction;
			this.crime = crime;
			this.until = until;
		}
	}

	/** Crook → their arrest. */
	private static final Map<UUID, Arrest> ARRESTS = new HashMap<>();

	private Arrests() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Arrests::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ARRESTS.clear());
	}

	/** {@code e} is being chased down, held or judged by the guards. */
	static boolean held(Entity e) {
		return ARRESTS.containsKey(e.getUUID());
	}

	/**
	 * (From {@link Crooks}) {@code crook} was seen at {@code crime} in {@code f}: the nearest free guard goes to arrest
	 * them. False if there's no guard to send (the golems deal with it, as before).
	 */
	static boolean arrest(ServerLevel level, Villager crook, Faction f, String crime) {
		if (ARRESTS.containsKey(crook.getUUID())) {
			return true;
		}
		Folk guard = null;
		double best = Double.MAX_VALUE;
		for (Folk g : level.getEntitiesOfClass(Folk.class, crook.getBoundingBox().inflate(CALL_RANGE),
			g -> g.isAlive() && g.factionId() == f.id() && g.combatant() && !g.leader() && g.getTarget() == null && !escorting(g))) {
			double d = g.distanceToSqr(crook);
			if (d < best) {
				best = d;
				guard = g;
			}
		}
		if (guard == null) {
			return false;
		}
		long now = level.getGameTime();
		ARRESTS.put(crook.getUUID(), new Arrest(crook, guard, f.id(), crime, now + CHASE_TICKS));
		say(level, guard, "halt");
		return true;
	}

	private static boolean escorting(Folk guard) {
		for (Arrest a : ARRESTS.values()) {
			if (a.guard == guard) {
				return true;
			}
		}
		return false;
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (ARRESTS.isEmpty()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		for (Iterator<Arrest> it = ARRESTS.values().iterator(); it.hasNext();) {
			Arrest a = it.next();
			if (!step(level, a, now)) {
				it.remove();
			}
		}
	}

	/** One tick of an arrest; false when it's over. */
	private static boolean step(ServerLevel level, Arrest a, long now) {
		Villager crook = a.crook;
		Folk guard = a.guard;
		if (!crook.isAlive() || crook.isRemoved() || crook.level() != level) {
			return false;
		}
		if (a.stage == Stage.EXILE) {
			return exile(level, a, now);
		}
		if (!guard.isAlive() || guard.isRemoved() || guard.level() != level || Downed.isDowned(guard) || now > a.until
			|| (a.stage != Stage.BEAT && guard.getTarget() != null && guard.getTarget() != crook)) {
			// the guard's fallen, been called off, or given up: the crook takes their chance
			if (guard.getTarget() == crook) {
				guard.setTarget(null);
			}
			if (!Downed.isDowned(crook)) {
				Crooks.flee(level, crook, guard.position());
			}
			return false;
		}
		Faction f = Factions.get(level.getServer(), a.faction);
		if (f == null) {
			return false;
		}
		switch (a.stage) {
			case CHASE -> {
				if (Downed.isDowned(crook)) {
					haul(level, a, now);
				} else if (guard.distanceTo(crook) <= HALT_RANGE) {
					RandomSource random = level.getRandom();
					float submit = Math.max(SUBMIT_MIN, SUBMIT - SUBMIT_PER_NOTORIETY * Crooks.notoriety(crook));
					if (random.nextFloat() < submit) {
						say(level, crook, "submit");
						Crooks.stopRunning(crook);
						lead(a, now);
					} else {
						say(level, crook, "resist");
						say(level, guard, "resisted");
						guard.setTarget(crook);
						a.stage = Stage.BEAT;
						a.until = now + BEAT_TICKS;
					}
				} else if (now % 10 == 0) {
					guard.getNavigation().moveTo(crook, GUARD_SPEED);
				}
			}
			case BEAT -> {
				if (Downed.isDowned(crook)) {
					haul(level, a, now);
				}
			}
			case LEAD -> {
				if (Downed.isDowned(crook)) {
					// (waiting for them to be got up)
					guard.getNavigation().stop();
					guard.getLookControl().setLookAt(crook, 30.0F, 30.0F);
					return true;
				}
				LivingEntity judge = judge(level, f);
				Vec3 to = judge != null ? judge.position() : Vec3.atBottomCenterOf(f.seat());
				if (guard.position().distanceTo(to) <= AUDIENCE_RANGE && crook.distanceTo(guard) <= AUDIENCE_RANGE + 2.0) {
					guard.getNavigation().stop();
					a.stage = Stage.AUDIENCE;
					a.next = now + VERDICT_TICKS;
					// "your majesty, this one was caught..."
					Speech.say(level, guard, Component.translatable("arrest.jaymoskingdoms.present." + level.getRandom().nextInt(LINES),
						Component.translatable("crime.jaymoskingdoms." + a.crime)), Speech.YELLOW, AUDIENCE_SPEECH);
					return true;
				}
				if (now % 10 == 0) {
					// the guard leads, a little slower when the prisoner lags
					double pace = crook.distanceTo(guard) > 6.0 ? GUARD_SPEED * 0.5 : GUARD_SPEED * 0.8;
					guard.getNavigation().moveTo(to.x, to.y, to.z, pace);
					BehaviorUtils.setWalkAndLookTargetMemories(crook, new EntityTracker(guard, false), PRISONER_SPEED, 1);
				}
			}
			case AUDIENCE -> {
				hold(level, a, f);
				if (now >= a.next) {
					pronounce(level, a, f);
					a.stage = Stage.PUNISH;
					a.next = now + CARRY_OUT_TICKS;
				}
			}
			case PUNISH -> {
				hold(level, a, f);
				if (now >= a.next) {
					return punish(level, a, f, now);
				}
			}
			default -> {
			}
		}
		return true;
	}

	/** The crook is down: the guard stops beating them, and has them got up (Jaymo's Powers revives them), to march them off. */
	private static void haul(ServerLevel level, Arrest a, long now) {
		a.guard.setTarget(null);
		a.crook.addTag(Downed.REVIVE_TAG);
		Crooks.stopRunning(a.crook);
		say(level, a.guard, "hauled");
		lead(a, now);
	}

	private static void lead(Arrest a, long now) {
		a.stage = Stage.LEAD;
		a.until = now + LEAD_TICKS;
	}

	/** The ruler, if he's about (alive, loaded, not fighting); else null, and the audience is held at the seat. */
	private static @Nullable LivingEntity judge(ServerLevel level, Faction f) {
		if (f.leader().isPresent() && level.getEntity(f.leader().get()) instanceof Folk ruler && ruler.isAlive() && ruler.getTarget() == null) {
			return ruler;
		}
		return null;
	}

	/** At the audience: everyone stands still and faces the ruler (or the guard, with none). */
	private static void hold(ServerLevel level, Arrest a, Faction f) {
		LivingEntity judge = judge(level, f);
		Entity facing = judge != null ? judge : a.guard;
		a.guard.getNavigation().stop();
		a.crook.getNavigation().stop();
		a.crook.getLookControl().setLookAt(facing, 30.0F, 30.0F);
		if (judge != null) {
			a.guard.getLookControl().setLookAt(judge, 30.0F, 30.0F);
			if (judge instanceof Folk ruler) {
				ruler.getNavigation().stop();
				ruler.getLookControl().setLookAt(a.crook, 30.0F, 30.0F);
			}
		}
	}

	/** The ruler (or, with none about, the guard in his name) weighs it up and says the sentence aloud; nearby players are told. */
	private static void pronounce(ServerLevel level, Arrest a, Faction f) {
		RandomSource random = level.getRandom();
		Tempers.Temper temper = Tempers.of(f);
		int lean = TEMPER_LEAN[temper == null ? TEMPER_LEAN.length - 1 : temper.ordinal()];
		int score = Crooks.notoriety(a.crook) * PER_NOTORIETY + lean + random.nextInt(CHANCE_SPREAD + 1);
		a.sentence = score >= EXECUTE_FROM ? Sentence.EXECUTE : score >= EXILE_FROM ? Sentence.EXILE : score >= FLOG_FROM ? Sentence.FLOG : Sentence.PARDON;
		LivingEntity judge = judge(level, f);
		LivingEntity speaker = judge != null ? judge : a.guard;
		Speech.say(level, speaker, Component.translatable("arrest.jaymoskingdoms.verdict." + a.sentence.key() + "." + random.nextInt(LINES)),
			a.sentence == Sentence.PARDON ? Speech.GOLD : Speech.RED, AUDIENCE_SPEECH);
		Component judgeName = judge != null ? judge.getDisplayName() : Component.literal(f.name());
		Component told = Component.translatable("message.jaymoskingdoms.arrest.sentence", judgeName, a.crook.getDisplayName(),
			Component.translatable("crime.jaymoskingdoms." + a.crime), Component.translatable("sentence.jaymoskingdoms." + a.sentence.key()))
			.withStyle(ChatFormatting.GOLD);
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, a.crook.getBoundingBox().inflate(ANNOUNCE_RANGE))) {
			p.sendSystemMessage(told);
		}
	}

	/** The sentence is carried out; false when it's done (an exile goes on walking). */
	private static boolean punish(ServerLevel level, Arrest a, Faction f, long now) {
		Villager crook = a.crook;
		Folk guard = a.guard;
		RandomSource random = level.getRandom();
		switch (a.sentence) {
			case PARDON -> {
				say(level, crook, "thanks");
				done(a);
				return false;
			}
			case FLOG -> {
				guard.getLookControl().setLookAt(crook, 30.0F, 30.0F);
				guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				if (crook.getHealth() > FLOG_SPARES) {
					crook.hurtServer(level, level.damageSources().mobAttack(guard), FLOG_DAMAGE);
				}
				level.playSound(null, crook.getX(), crook.getY(), crook.getZ(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 1.0F, 1.0F);
				if (++a.blows < FLOG_BLOWS) {
					a.next = now + FLOG_GAP;
					return true;
				}
				say(level, crook, "flogged");
				done(a);
				return false;
			}
			case EXILE -> {
				say(level, crook, "exiled");
				Vec3 away = crook.position().subtract(Vec3.atBottomCenterOf(f.seat())).multiply(1.0, 0.0, 1.0);
				if (away.lengthSqr() < 1.0E-4) {
					away = new Vec3(random.nextDouble() - 0.5, 0.0, random.nextDouble() - 0.5);
				}
				a.away = crook.position().add(away.normalize().scale(EXILE_DISTANCE));
				a.stage = Stage.EXILE;
				a.until = now + EXILE_TICKS;
				// (not one of the town's any more)
				crook.setAttached(Crooks.CROOK, false);
				done(a);
				return true;
			}
			case EXECUTE -> {
				guard.getLookControl().setLookAt(crook, 30.0F, 30.0F);
				guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				level.playSound(null, crook.getX(), crook.getY(), crook.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 1.0F, 0.8F);
				done(a);
				// beheaded: nothing to get up from
				crook.addTag(HEADLESS_TAG);
				crook.kill(level);
				return false;
			}
		}
		return false;
	}

	/** The exile walks out of the realm, and is gone. */
	private static boolean exile(ServerLevel level, Arrest a, long now) {
		Villager crook = a.crook;
		if (now > a.until || crook.position().distanceToSqr(a.away) < 9.0) {
			crook.discard();
			return false;
		}
		if (now % 10 == 0) {
			BehaviorUtils.setWalkAndLookTargetMemories(crook, BlockPos.containing(a.away), PRISONER_SPEED, 2);
		}
		return true;
	}

	private static void done(Arrest a) {
		for (Sentenced s : SENTENCED) {
			s.sentenced(a.crook, a.faction, a.sentence.key());
		}
	}

	private static void say(ServerLevel level, Entity who, String key) {
		Speech.say(level, who, Component.translatable("arrest.jaymoskingdoms." + key + "." + level.getRandom().nextInt(LINES)),
			key.equals("submit") || key.equals("thanks") ? Speech.WHITE : Speech.RED, AUDIENCE_SPEECH);
	}
}
