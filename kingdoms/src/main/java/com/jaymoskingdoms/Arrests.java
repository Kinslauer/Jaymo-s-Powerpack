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
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.126) Andy (2026-10-21): "can you make guards attempt to arrest before killing criminals? either give them a visible
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
 *
 * <p>(0.7.132) Andy: "you can also drop a downed one in front of a guard for judgement (you'll later hear or see them
 * jailed or executed, or freed!)". A downed looter ({@link Looters}) set down before a guard is handed over
 * ({@link #handOver}): got up and marched to the same audience. A looter is never exiled: that sentence is the cells
 * ({@link Sentence#JAIL}), where the guard walks him off to the seat and he's taken below. Whoever brought him in sees it
 * if they're by, or hears of it later from the townsfolk ({@link Looters#news}).
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
	/** (0.7.138) An MCA guard beats a crook down by hand (their brain is set aside for the arrest): this hard, this often (ticks), this close. */
	static final float VILLAGER_BLOW = 3.0F;
	static final int BLOW_TICKS = 20;
	static final double BLOW_REACH = 2.5;
	/** At the audience: the guard speaks, then the ruler after this long, then the sentence is carried out after this (ticks). */
	static final int VERDICT_TICKS = 20 * 4, CARRY_OUT_TICKS = 20 * 3;
	/** How far words at an audience carry, and how far players are told the sentence in chat. */
	static final double AUDIENCE_SPEECH = 24.0, ANNOUNCE_RANGE = 48.0;
	/** Sentencing: each notoriety counts this much, plus up to this at random; the ruler's lean by temper (TYRANT, NOBLE, WARLORD, PARANOID, REVELER, PURIST, none). */
	static final int PER_NOTORIETY = 10, CHANCE_SPREAD = 30;
	static final int[] TEMPER_LEAN = {30, -20, 15, 20, -10, 25, 0};
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
		PARDON, FLOG, EXILE, EXECUTE,
		/** (0.7.132) Locked in the cells (a looter's in place of exile). */
		JAIL;

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

	/** (0.7.132) A jailed looter is walked off to the cells this long at most (ticks). */
	static final int JAIL_TICKS = 20 * 30;

	private static final class Arrest {
		/** A villager crook ({@link Crooks}), or (0.7.132) a looter handed over. */
		final PathfinderMob crook;
		/** A folk guard, or (0.7.138) one of the realm's MCA guards. */
		final PathfinderMob guard;
		final int faction;
		final String crime;
		/** (0.7.132) How notorious: a crook's own, a looter's by the bodies he robbed. */
		int notoriety;
		/** (0.7.132) The player who brought him in (handed over), or null. */
		@Nullable UUID bringer;
		Stage stage = Stage.CHASE;
		long until, next;
		@Nullable Sentence sentence;
		int blows;
		Vec3 away = Vec3.ZERO;

		Arrest(PathfinderMob crook, PathfinderMob guard, int faction, String crime, long until) {
			this.crook = crook;
			this.guard = guard;
			this.faction = faction;
			this.crime = crime;
			this.until = until;
			this.notoriety = Crooks.notoriety(crook);
		}

		boolean looter() {
			return Looters.is(crook);
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
		PathfinderMob guard = null;
		double best = Double.MAX_VALUE;
		// (0.7.138) folk guards, and the realm's MCA guards not on their ruler's escort
		for (Worker w : Workers.near(level, crook.getBoundingBox().inflate(CALL_RANGE),
			g -> g.isAlive() && g.factionId() == f.id() && (g instanceof Folk folk ? folk.combatant() && !folk.leader() : g.role() == Folk.GUARD && Royals.escortOf(g) == null)
				&& Rulers.target(g.mob()) == null && !escorting(g.mob()))) {
			double d = w.mob().distanceToSqr(crook);
			if (d < best) {
				best = d;
				guard = w.mob();
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

	/**
	 * (0.7.132) A downed looter is set down before {@code guard}: the guard has him got up and marches him to the ruler for
	 * judgement. {@code bringer} brought him in.
	 */
	static void handOver(ServerLevel level, PathfinderMob looter, PathfinderMob guard, Faction f, String crime, int notoriety, ServerPlayer bringer) {
		if (ARRESTS.containsKey(looter.getUUID())) {
			return;
		}
		Arrest a = new Arrest(looter, guard, f.id(), crime, level.getGameTime() + LEAD_TICKS);
		a.notoriety = notoriety;
		a.bringer = bringer.getUUID();
		ARRESTS.put(looter.getUUID(), a);
		guard.getLookControl().setLookAt(looter, 30.0F, 30.0F);
		haul(level, a, level.getGameTime());
	}

	/** (0.7.132) {@code guard} is taking someone to judgement just now. */
	static boolean busy(Entity guard) {
		return escorting(guard);
	}

	private static boolean escorting(Entity guard) {
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

	/** (0.7.138) {@code ruler} is hearing a case just now (a villager ruler stands still for it, {@link Rulers#step}). */
	static boolean judging(Entity ruler) {
		for (Arrest a : ARRESTS.values()) {
			if ((a.stage == Stage.AUDIENCE || a.stage == Stage.PUNISH) && Rulers.faction(ruler) == a.faction && Rulers.is(ruler)) {
				return true;
			}
		}
		return false;
	}

	/** One tick of an arrest; false when it's over. */
	private static boolean step(ServerLevel level, Arrest a, long now) {
		PathfinderMob crook = a.crook;
		PathfinderMob guard = a.guard;
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
				if (crook instanceof Villager v) {
					Crooks.flee(level, v, guard.position());
				} else {
					Looters.escaped(level, crook, guard.position());
				}
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
					float submit = Math.max(SUBMIT_MIN, SUBMIT - SUBMIT_PER_NOTORIETY * a.notoriety);
					if (random.nextFloat() < submit) {
						say(level, crook, "submit");
						if (crook instanceof Villager v) {
							Crooks.stopRunning(v);
						}
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
				} else if (!(guard instanceof Folk) && now % BLOW_TICKS == 0) {
					// (0.7.138) an MCA guard: by hand
					if (guard.distanceTo(crook) <= BLOW_REACH) {
						guard.getLookControl().setLookAt(crook, 30.0F, 30.0F);
						guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
						crook.hurtServer(level, level.damageSources().mobAttack(guard), VILLAGER_BLOW);
					} else {
						guard.getNavigation().moveTo(crook, GUARD_SPEED);
					}
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
					follow(crook, guard);
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
		if (a.crook instanceof Villager v) {
			Crooks.stopRunning(v);
		}
		say(level, a.guard, "hauled");
		lead(a, now);
	}

	/** (0.7.132) The prisoner walks at the guard's back: a villager by its brain, a looter by his navigation. */
	private static void follow(PathfinderMob crook, PathfinderMob guard) {
		if (crook instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(guard, false), PRISONER_SPEED, 1);
		} else if (crook.distanceToSqr(guard) > 2.0 * 2.0) {
			crook.getNavigation().moveTo(guard, 1.0);
		}
	}

	private static void lead(Arrest a, long now) {
		a.stage = Stage.LEAD;
		a.until = now + LEAD_TICKS;
	}

	/** The ruler, if he's about (alive, loaded, not fighting); else null, and the audience is held at the seat. */
	private static @Nullable LivingEntity judge(ServerLevel level, Faction f) {
		// (0.7.138) a folk ruler, or a villager crowned
		PathfinderMob ruler = Rulers.of(level, f);
		return ruler != null && Rulers.target(ruler) == null ? ruler : null;
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
			if (judge instanceof PathfinderMob ruler) {
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
		int score = a.notoriety * PER_NOTORIETY + lean + random.nextInt(CHANCE_SPREAD + 1);
		a.sentence = score >= EXECUTE_FROM ? Sentence.EXECUTE : score >= EXILE_FROM ? Sentence.EXILE : score >= FLOG_FROM ? Sentence.FLOG : Sentence.PARDON;
		if (a.sentence == Sentence.EXILE && a.looter()) {
			// (0.7.132) no realm lets a looter go to rob the next field: the cells
			a.sentence = Sentence.JAIL;
		}
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
		PathfinderMob crook = a.crook;
		PathfinderMob guard = a.guard;
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
			case JAIL -> {
				say(level, crook, "jailed");
				// walked off to the cells under the seat, the guard at his back
				a.away = Vec3.atBottomCenterOf(f.seat());
				a.stage = Stage.EXILE;
				a.until = now + JAIL_TICKS;
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
		PathfinderMob crook = a.crook;
		if (now > a.until || crook.position().distanceToSqr(a.away) < 9.0) {
			if (a.sentence == Sentence.JAIL) {
				// taken below
				level.playSound(null, crook.getX(), crook.getY(), crook.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 1.0F, 0.8F);
			}
			crook.discard();
			return false;
		}
		if (now % 10 == 0) {
			if (crook instanceof Villager v) {
				BehaviorUtils.setWalkAndLookTargetMemories(v, BlockPos.containing(a.away), PRISONER_SPEED, 2);
			} else {
				crook.getNavigation().moveTo(a.away.x, a.away.y, a.away.z, 0.9);
			}
			if (a.sentence == Sentence.JAIL && a.guard.isAlive()) {
				a.guard.getNavigation().moveTo(crook, 1.0);
			}
		}
		return true;
	}

	private static void done(Arrest a) {
		for (Sentenced s : SENTENCED) {
			s.sentenced(a.crook, a.faction, a.sentence.key());
		}
		if (a.bringer != null && a.crook.level() instanceof ServerLevel level) {
			Looters.judged(level, a.bringer, a.faction, a.crook, a.sentence.key());
		}
	}

	private static void say(ServerLevel level, Entity who, String key) {
		Speech.say(level, who, Component.translatable("arrest.jaymoskingdoms." + key + "." + level.getRandom().nextInt(LINES)),
			key.equals("submit") || key.equals("thanks") ? Speech.WHITE : Speech.RED, AUDIENCE_SPEECH);
	}
}
