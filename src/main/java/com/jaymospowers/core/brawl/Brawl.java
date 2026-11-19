package com.jaymospowers.core.brawl;

import com.jaymospowers.combat.ClashFx;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.gifted.Gifted;
import com.jaymospowers.core.gifted.GiftKit;
import com.jaymospowers.power.zfighter.ZFighterCombo;
import com.jaymospowers.power.zfighter.ZFighterGuard;
import com.jaymospowers.power.zfighter.ZFighterKingdoms;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The fighter brain every powered NPC runs (NPC fights patch 1): tyrants and raiders ({@code ZFighterRogue}), Galactic
 * Patrolmen, and every power's gifted villagers ({@code GiftedAI}). One state per fighting NPC, ticked by its owner's AI
 * ({@link #tick}) and stepped every tick here, so the moves are the same whoever throws them and all of them work
 * on any foe: players too.
 * <ul>
 * <li><b>Rank</b> ({@link #rank}): one number from 0 to 1 per fighter, from what it is ({@link Profile#base}) and the world tier
 * of the nearest player. Everything below reads it; at 0 a fighter behaves about as before.</li>
 * <li><b>Flight</b>: a fighter whose power flies ({@link Profile#flies}) takes off when its foe is in the air (or it was thrown
 * up), hovers at the foe's height, circles at 3 to 6 blocks, dives in for its blows, and lands once the foe has been on the
 * ground a while. {@link #flyTo} is the same flight for a trip: a far target, at cruise height.</li>
 * <li><b>Dash and strafe</b>: a burst in from 6 to 12 blocks, sidesteps and circling, between blows.</li>
 * <li><b>Vanish dodge</b>: a blow, blast or beam about to land may be dodged by blinking behind whoever threw it, leaving a
 * fading silhouette ({@link #blink}); {@link #takeVanish} tells the owner so its next string starts at once.</li>
 * <li><b>Guard and parry</b>: crossed arms ({@link ZFighterGuard#GUARD}, drawn on a rogue by the client) against an attacker
 * in front, half the damage; a blow in the first moments is parried (sparks, the attacker staggered, a counter).</li>
 * <li><b>Tempo</b>: {@link #byRank} and {@link #tempo} let an owner think faster and rest less as rank grows.</li>
 * </ul>
 * Flight steering and the strafe and counter run every server tick; the decisions run whenever the owner calls
 * {@link #tick} (a state not ticked for {@link #STALE} ticks lets its fighter down and is forgotten).
 */
public final class Brawl {
	// ---- rank ----
	/** The most the nearest player's world tier (0 to {@link #MAX_TIER}) adds to a fighter's base rank. */
	static final float TIER_BONUS = 0.3F;
	static final int MAX_TIER = 4;
	static final double RANK_PLAYER_RANGE = 64.0;
	static final int RANK_EVERY = 100;
	/** Below this rank nothing but flight happens (a fighter that behaves as it always has). */
	static final float MIN_RANK = 0.02F;
	// ---- flight ----
	/** The foe is this far above (blocks), or off the ground and higher than {@link #AIR_ABOVE}, and the fighter takes off. */
	static final double AIR_GAP = 2.5, AIR_ABOVE = 1.0;
	/** Lands after the foe has been on the ground this long (ticks). */
	static final int LAND_AFTER = 60;
	/** Hover and circling speed (blocks a tick) at rank 0 and the most rank adds; diving likewise. */
	static final double FLY_SPEED = 0.45, FLY_SPEED_RANK = 0.3, DIVE_SPEED = 0.8, DIVE_SPEED_RANK = 0.3;
	static final double ORBIT_MIN = 3.0, ORBIT_MAX = 6.0, ORBIT_STEP = 0.1, ORBIT_HOVER = 0.5, ORBIT_BOB = 0.4;
	/** An orbit lasts this many ticks at rank 0 and 1 (plus up to {@code ORBIT_JITTER}); a dive at most {@code DIVE_MAX}; a hold in reach this much. */
	static final int ORBIT_SLOW = 90, ORBIT_FAST = 40, ORBIT_JITTER = 30, DIVE_MAX = 24, HOLD_MIN = 30, HOLD_JITTER = 30;
	static final double DIVE_FROM = 14.0, DIVE_REACH = 2.0, HOLD_GAP = 1.7, HOLD_SPEED = 0.35;
	/** The look ahead for walls (blocks) and the climb to clear them (blocks a tick). */
	static final double PROBE = 3.0, CLIMB = 0.3;
	/** How close to the world floor and the build limit it keeps (blocks). */
	static final int FLOOR_MARGIN = 6, CEILING_MARGIN = 6;
	/** A state not ticked this long (ticks) is let down and dropped. */
	static final int STALE = 40;
	static final int SLOW_FALL_TICKS = 60;
	/** The trail is drawn every this many ticks. */
	static final int TRAIL_EVERY = 3;
	// ---- travel ----
	/** {@link #flyTo}: rises up to this far (blocks) over the target's height at cruise, sets down within {@link #ARRIVE}. */
	static final double CRUISE_RISE = 12.0, ARRIVE = 4.0;
	/** The cap on its speed (blocks a tick), the climb, and how long after the last call it keeps flying (ticks). */
	static final double TRAVEL_SPEED = 0.9, TRAVEL_CLIMB = 0.4;
	static final int TRAVEL_HOLD = 6;
	// ---- dash and strafe ----
	static final int DECIDE_EVERY = 10;
	static final double DASH_MIN = 6.0, DASH_MAX = 12.0, DASH_SPEED = 0.8, DASH_SPEED_RANK = 0.4, DASH_LIFT = 0.15;
	static final int DASH_GAP_SLOW = 160, DASH_GAP_FAST = 70;
	static final float DASH_CHANCE = 0.6F;
	static final double STRAFE_MIN = 3.0, STRAFE_MAX = 16.0, STRAFE_SPEED = 0.4, STRAFE_SPEED_RANK = 0.2;
	static final int STRAFE_MIN_TICKS = 6, STRAFE_JITTER = 6, STRAFE_GAP_SLOW = 80, STRAFE_GAP_FAST = 30, STRAFE_GAP_JITTER = 20;
	static final float STRAFE_CHANCE = 0.7F;
	// ---- vanish dodge ----
	/** The chance of a dodge at rank 0 and 1 (a rookie patrolman about a tenth, a tyrant about a third), its cooldown (ticks), how far behind (blocks). */
	static final float VANISH_MIN = 0.05F, VANISH_MAX = 0.4F;
	static final int VANISH_GAP = 60, VANISH_JITTER = 40;
	static final double BEHIND = 2.0;
	// ---- guard and parry ----
	/** The share of a blow the guard takes, the front it covers (degrees); the same as a player's ({@code ZFighterGuard}). */
	static final float BLOCK_SHARE = 0.5F;
	static final double FRONT_DEGREES = 150.0;
	/** A guard stays up this many ticks; a blow in the first {@code PARRY_TICKS} is parried; blows it takes before it breaks. */
	static final int GUARD_TICKS = 16, PARRY_TICKS = 8, GUARD_BREAK = 4;
	/** Between guards (ticks) at rank 0 and 1, after a break, and between parries. */
	static final int GUARD_GAP_SLOW = 60, GUARD_GAP_FAST = 25, BREAK_GAP = 80, PARRY_GAP = 20;
	/** The chance, each check, of raising a guard at an attacker, and of snapping one up as a blow arrives. */
	static final float RAISE_MIN = 0.1F, RAISE_MAX = 0.5F, SNAP_MAX = 0.3F;
	static final int GUARD_CHECK = 2, ATTACK_MEMORY = 30;
	static final double GUARD_NEAR = 5.0;
	/** The parried attacker is thrown back, slowed and weakened this long (ticks); the counter lands this many ticks on, for this share of the fighter's blow. */
	static final double PARRY_PUSH = 0.9, PARRY_LIFT = 0.25;
	static final int STAGGER_TICKS = 30, COUNTER_DELAY = 5;
	static final float COUNTER_SHARE = 1.6F, COUNTER_FALLBACK = 4.0F;
	// ---- tempo ----
	/** How much quicker a rank 1 fighter thinks and rests: this share of today's time. */
	static final float TEMPO_FASTEST = 0.5F;
	static final int DEFAULT_COLOR = 0xFFC830;

	/**
	 * What kind of fighter an owner runs: its base rank (0 to 1, before the world tier), whether its power flies, and the
	 * colour and kit (null for a rogue) its effects use.
	 */
	public record Profile(float base, boolean flies, int color, @Nullable GiftKit kit) {
		/** A ki fighter (a rogue, a patrolman without a kit): flies, gold. */
		public static Profile ki(float base, int color) {
			return new Profile(base, true, color, null);
		}

		/** A gifted fighter in its kit's own flavour (flies if the power does). */
		public static Profile gifted(float base, PathfinderMob mob) {
			Gifted.Gift gift = Gifted.of(mob);
			GiftKit kit = gift == null ? null : Gifted.kit(gift.power());
			return new Profile(base, kit != null && kit.flies(), kit == null ? DEFAULT_COLOR : kit.color(), kit);
		}
	}

	private enum Mode {
		NONE, COMBAT, TRAVEL
	}

	private enum Phase {
		ORBIT, DIVE, HOLD
	}

	private static final class State {
		Profile profile = Profile.ki(0.0F, DEFAULT_COLOR);
		long seen;
		float rank;
		long rankAt;
		// flight
		Mode mode = Mode.NONE;
		Phase phase = Phase.ORBIT;
		long phaseEnd;
		double angle;
		int dir = 1;
		double radius = ORBIT_MIN;
		long groundedSince = -1L;
		@Nullable LivingEntity foe;
		@Nullable Entity goal;
		long travelUntil;
		boolean engaged;
		long drivenUntil;
		@Nullable Vec3 last;
		// ground moves
		long decideAt, dashAt, strafeAt, strafeUntil;
		int strafeDir = 1;
		// vanish
		long vanishAt;
		boolean vanished;
		// guard
		long guardStart = -1L, guardUntil, guardAt, parryAt, checkAt;
		// a bitten feint leaves them open from here to there ({@link Feints})
		long openFrom, openUntil;
		int blows;
		long counterAt;
		@Nullable LivingEntity counterFoe;
	}

	private static final Map<Mob, State> STATES = new WeakHashMap<>();

	private Brawl() {
	}

	/** From the mod's init: the damage hooks and the per-tick step. */
	public static void init() {
		// (before the vanish dodge, so a blow it cancels still counts as having landed)
		Feints.init();
		ServerTickEvents.END_SERVER_TICK.register(Brawl::step);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Brawl::allowDamage);
	}

	// ================================================================ the owner's calls

	/**
	 * The owner's call, every tick or every few: the fighter {@code mob} (of kind {@code profile}) fights {@code foe} (null:
	 * none, so it lets itself down). {@code engaged}: it is in the middle of a blow or string (it holds still in reach).
	 * {@code driving}: the owner moves the body itself this tick (a rush, the high sky's own flight), so the brain doesn't.
	 */
	public static void tick(ServerLevel level, Mob mob, @Nullable LivingEntity foe, Profile profile, boolean engaged, boolean driving) {
		long now = level.getGameTime();
		State s = STATES.get(mob);
		if (s == null) {
			if (foe == null) {
				return;
			}
			s = new State();
			STATES.put(mob, s);
		}
		s.profile = profile;
		s.seen = now;
		s.engaged = engaged;
		if (driving) {
			s.drivenUntil = now + 2;
		}
		if (now >= s.rankAt) {
			s.rank = rankOf(level, mob, profile.base());
			s.rankAt = now + RANK_EVERY;
		}
		if (foe == null || !foe.isAlive() || foe.isRemoved() || foe.level() != mob.level()) {
			s.foe = null;
			if (s.mode == Mode.COMBAT) {
				land(mob, s);
			}
			return;
		}
		s.foe = foe;
		if (profile.flies() && s.mode != Mode.TRAVEL && !kitFlying(mob, profile)) {
			decideFlight(level, mob, s, foe, now);
		}
		if (s.rank >= MIN_RANK) {
			decideGround(level, mob, s, foe, now);
			decideGuard(level, mob, s, foe, now);
			Feints.throwFeint(level, mob, foe, s.rank, s.engaged || now < s.drivenUntil, now);
		}
	}

	/**
	 * Flies {@code mob} toward {@code target}, a far one: call it every tick (or every few) while the trip lasts. It
	 * lifts off, climbs to cruise height (up to {@link #CRUISE_RISE} over the target), flies at a capped speed
	 * ({@link #TRAVEL_SPEED}), trailing its power's mark ({@link GiftKit#trail}, else gold ki), checks ahead for blocks on the
	 * server, never goes below the world floor or above the build limit, and comes down within {@link #ARRIVE} blocks of
	 * the target. True once it has arrived (or the trip can't be made: the target is gone or in another world), false while
	 * it is still on its way. If the calls stop it lands within {@link #TRAVEL_HOLD} ticks. Works for any {@link Mob}; a
	 * fighter that has also called {@link #tick} keeps its colour and flavour.
	 */
	public static boolean flyTo(Mob mob, Entity target) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return true;
		}
		State s = STATES.get(mob);
		if (target == null || !target.isAlive() || target.isRemoved() || target.level() != mob.level()) {
			if (s != null && s.mode == Mode.TRAVEL) {
				land(mob, s);
			}
			return true;
		}
		if (mob.distanceTo(target) <= ARRIVE) {
			if (s != null && s.mode == Mode.TRAVEL) {
				land(mob, s);
			}
			return true;
		}
		long now = level.getGameTime();
		if (s == null) {
			s = new State();
			if (mob instanceof PathfinderMob p && Gifted.of(p) != null) {
				s.profile = Profile.gifted(0.0F, p);
			}
			STATES.put(mob, s);
		}
		s.seen = now;
		if (s.mode != Mode.TRAVEL) {
			if (mob.isInWater() || mob.isPassenger()) {
				return false;
			}
			s.mode = Mode.TRAVEL;
			s.last = null;
			mob.setNoGravity(true);
			mob.getNavigation().stop();
			mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, TRAVEL_CLIMB, 0.0));
			mob.syncVelocity = true;
		}
		s.goal = target;
		s.travelUntil = now + TRAVEL_HOLD;
		return false;
	}

	/** Whether the brain has {@code mob} in the air just now. */
	public static boolean flying(Mob mob) {
		State s = STATES.get(mob);
		return s != null && s.mode != Mode.NONE;
	}

	/** Whether {@code mob}'s power flies (the brain can take it up after a foe in the air). */
	public static boolean flier(Mob mob) {
		State s = STATES.get(mob);
		return s != null && s.profile.flies();
	}

	/** {@code mob}'s rank (0 to 1), or 0 if it isn't fighting. */
	public static float rank(Mob mob) {
		State s = STATES.get(mob);
		return s == null ? 0.0F : s.rank;
	}

	/** {@code from} (rank 0) to {@code to} (rank 1) for {@code mob}: a think interval that shrinks with rank, say. */
	public static int byRank(Mob mob, int from, int to) {
		return Math.round(Mth.lerp(rank(mob), (float)from, (float)to));
	}

	/** {@code ticks} (a gap or a rest) as {@code mob} takes it: down to {@link #TEMPO_FASTEST} of it at rank 1. */
	public static int tempo(Mob mob, int ticks) {
		return Math.round(ticks * Mth.lerp(rank(mob), 1.0F, TEMPO_FASTEST));
	}

	/** True once after {@code mob} vanish-dodged: its owner starts the next string at once. */
	public static boolean takeVanish(Mob mob) {
		State s = STATES.get(mob);
		if (s == null || !s.vanished) {
			return false;
		}
		s.vanished = false;
		return true;
	}

	// ================================================================ for Feints (the same package)

	/** The brawlers within {@code range} of {@code who} that the brain ticked lately, with their ranks. */
	static Map<Mob, Float> brawlersNear(LivingEntity who, double range, long now) {
		Map<Mob, Float> found = new java.util.HashMap<>();
		for (Map.Entry<Mob, State> e : STATES.entrySet()) {
			Mob mob = e.getKey();
			State s = e.getValue();
			if (mob != null && mob != who && s.rank >= MIN_RANK && now - s.seen <= STALE && mob.isAlive() && mob.level() == who.level()
				&& mob.distanceToSqr(who) <= range * range) {
				found.put(mob, s.rank);
			}
		}
		return found;
	}

	/** {@code mob} flinches into a guard of {@code guardTicks}, then is open for {@code openTicks} (a feint bit). */
	static void flinch(Mob mob, long now, int guardTicks, int openTicks) {
		State s = STATES.get(mob);
		if (s == null) {
			return;
		}
		raise(mob, s, now);
		s.guardUntil = now + guardTicks;
		s.openFrom = now + guardTicks;
		s.openUntil = s.openFrom + openTicks;
		s.guardAt = s.openUntil;
	}

	/** A quick sidestep for {@code mob}, a few ticks, either way. */
	static void sidestep(Mob mob, long now) {
		State s = STATES.get(mob);
		if (s != null) {
			s.strafeDir = mob.getRandom().nextBoolean() ? 1 : -1;
			s.strafeUntil = now + STRAFE_MIN_TICKS;
		}
	}

	/** {@code mob} blinks behind {@code attacker} now, if its dodge is ready. */
	static boolean vanishNow(ServerLevel level, Mob mob, LivingEntity attacker, long now) {
		State s = STATES.get(mob);
		return s != null && now >= s.vanishAt && vanish(level, mob, s, attacker, now);
	}

	/** {@code mob}'s counter on {@code foe} lands at game time {@code at} (as after a parry). */
	static void counterAt(Mob mob, LivingEntity foe, long at) {
		State s = STATES.get(mob);
		if (s != null) {
			s.counterFoe = foe;
			s.counterAt = at;
		}
	}

	// ================================================================ rank

	private static float rankOf(ServerLevel level, Mob mob, float base) {
		Player near = level.getNearestPlayer(mob, RANK_PLAYER_RANGE);
		int tier = near == null ? 0 : Math.clamp(ZFighterKingdoms.worldTier(near), 0, MAX_TIER);
		return Mth.clamp(base + TIER_BONUS * tier / MAX_TIER, 0.0F, 1.0F);
	}

	// ================================================================ flight decisions

	private static boolean kitFlying(Mob mob, Profile profile) {
		return profile.kit() != null && mob instanceof PathfinderMob p && profile.kit().flyingBy(p);
	}

	/** Whether {@code e} is off the ground with nothing to stand on just below. */
	private static boolean airborne(ServerLevel level, Entity e) {
		return !e.onGround() && !e.isInWater() && level.noCollision(e, e.getBoundingBox().expandTowards(0.0, -AIR_GAP, 0.0));
	}

	private static void decideFlight(ServerLevel level, Mob mob, State s, LivingEntity foe, long now) {
		boolean up = foe.getY() > mob.getY() + AIR_GAP || airborne(level, foe) && foe.getY() > mob.getY() + AIR_ABOVE;
		boolean thrown = mob.hurtTime > 0 && !mob.onGround() && mob.getDeltaMovement().y > 0.3;
		if (s.mode == Mode.NONE) {
			if ((up || thrown) && !mob.isInWater() && !mob.isPassenger()) {
				s.mode = Mode.COMBAT;
				s.phase = Phase.ORBIT;
				s.phaseEnd = now + orbitTicks(mob, s);
				s.groundedSince = -1L;
				s.last = null;
				mob.setNoGravity(true);
				mob.getNavigation().stop();
				mob.setDeltaMovement(mob.getDeltaMovement().add(0.0, TRAVEL_CLIMB, 0.0));
				mob.syncVelocity = true;
				if (mob.level() instanceof ServerLevel l) {
					l.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.HOSTILE, 0.8F, 1.3F);
				}
			}
			return;
		}
		if (s.mode != Mode.COMBAT) {
			return;
		}
		if (!airborne(level, foe) && !up) {
			if (s.groundedSince < 0L) {
				s.groundedSince = now;
			} else if (now - s.groundedSince >= LAND_AFTER) {
				land(mob, s);
			}
		} else {
			s.groundedSince = -1L;
		}
	}

	private static int orbitTicks(Mob mob, State s) {
		return Math.round(Mth.lerp(s.rank, (float)ORBIT_SLOW, (float)ORBIT_FAST)) + mob.getRandom().nextInt(ORBIT_JITTER);
	}

	private static void land(Mob mob, State s) {
		boolean wasFlying = s.mode != Mode.NONE;
		s.mode = Mode.NONE;
		s.goal = null;
		s.last = null;
		s.groundedSince = -1L;
		if (wasFlying) {
			mob.setNoGravity(false);
			if (!mob.onGround()) {
				mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false));
			}
			mob.resetFallDistance();
		}
	}

	// ================================================================ dash and strafe decisions

	private static void decideGround(ServerLevel level, Mob mob, State s, LivingEntity foe, long now) {
		if (s.mode != Mode.NONE || now < s.decideAt || s.engaged || now < s.drivenUntil || !mob.onGround() || mob.isPassenger()) {
			return;
		}
		s.decideAt = now + DECIDE_EVERY;
		double distance = mob.distanceTo(foe);
		if (distance >= DASH_MIN && distance <= DASH_MAX && now >= s.dashAt && mob.getRandom().nextFloat() < DASH_CHANCE * s.rank && mob.hasLineOfSight(foe)) {
			Vec3 to = foe.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > 1.0E-4) {
				double speed = DASH_SPEED + DASH_SPEED_RANK * s.rank;
				Vec3 v = to.normalize().scale(speed);
				mob.setDeltaMovement(v.x, DASH_LIFT, v.z);
				mob.syncVelocity = true;
				s.dashAt = now + Math.round(Mth.lerp(s.rank, (float)DASH_GAP_SLOW, (float)DASH_GAP_FAST));
				trail(level, mob, s, mob.position().add(0.0, mob.getBbHeight() * 0.5, 0.0), 6);
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BREEZE_JUMP, SoundSource.HOSTILE, 0.7F, 1.5F);
			}
			return;
		}
		if (distance >= STRAFE_MIN && distance <= STRAFE_MAX && now >= s.strafeAt && mob.getRandom().nextFloat() < STRAFE_CHANCE * s.rank) {
			s.strafeDir = mob.getRandom().nextBoolean() ? 1 : -1;
			s.strafeUntil = now + STRAFE_MIN_TICKS + mob.getRandom().nextInt(STRAFE_JITTER);
			s.strafeAt = now + Math.round(Mth.lerp(s.rank, (float)STRAFE_GAP_SLOW, (float)STRAFE_GAP_FAST)) + mob.getRandom().nextInt(STRAFE_GAP_JITTER);
		}
	}

	// ================================================================ guard decisions

	/** Whether {@code foe} is on the attack against {@code mob}: in a Z Fighter string, or has been trading blows. */
	private static boolean attacking(Mob mob, LivingEntity foe) {
		return foe.hasAttached(ZFighterCombo.MOVE)
			|| mob.getLastHurtByMob() == foe && mob.tickCount - mob.getLastHurtByMobTimestamp() < ATTACK_MEMORY
			|| foe.getLastHurtMob() == mob && foe.tickCount - foe.getLastHurtMobTimestamp() < ATTACK_MEMORY;
	}

	private static void decideGuard(ServerLevel level, Mob mob, State s, LivingEntity foe, long now) {
		if (s.guardStart >= 0L && now >= s.guardUntil) {
			drop(mob, s, now, 0);
		}
		if (now < s.checkAt) {
			return;
		}
		s.checkAt = now + GUARD_CHECK;
		if (s.guardStart >= 0L || now < s.guardAt || s.engaged || now < s.drivenUntil || mob.distanceTo(foe) > GUARD_NEAR || !attacking(mob, foe)) {
			return;
		}
		if (mob.getRandom().nextFloat() < Mth.lerp(s.rank, RAISE_MIN, RAISE_MAX)) {
			raise(mob, s, now);
		}
	}

	private static void raise(Mob mob, State s, long now) {
		s.guardStart = now;
		s.guardUntil = now + GUARD_TICKS;
		s.blows = 0;
		mob.setAttached(ZFighterGuard.GUARD, true);
	}

	private static void drop(Mob mob, State s, long now, int breakGap) {
		s.guardStart = -1L;
		mob.removeAttached(ZFighterGuard.GUARD);
		s.guardAt = now + Math.max(breakGap, Math.round(Mth.lerp(s.rank, (float)GUARD_GAP_SLOW, (float)GUARD_GAP_FAST)));
	}

	// ================================================================ the per-tick step

	private static void step(MinecraftServer server) {
		if (STATES.isEmpty()) {
			return;
		}
		for (Map.Entry<Mob, State> entry : new ArrayList<>(STATES.entrySet())) {
			Mob mob = entry.getKey();
			State s = entry.getValue();
			if (mob == null || mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
				STATES.remove(mob);
				continue;
			}
			if (!mob.isAlive()) {
				s.mode = Mode.NONE;
				mob.removeAttached(ZFighterGuard.GUARD);
				STATES.remove(mob);
				continue;
			}
			long now = level.getGameTime();
			if (now - s.seen > STALE || mob.entityTags().contains("jaymospowers_downed")) {
				land(mob, s);
				if (s.guardStart >= 0L) {
					drop(mob, s, now, 0);
				}
				STATES.remove(mob);
				continue;
			}
			counter(level, mob, s, now);
			if (s.mode != Mode.NONE && now >= s.drivenUntil && !kitFlying(mob, s.profile)) {
				fly(level, mob, s, now);
			} else if (s.mode != Mode.NONE) {
				mob.setNoGravity(true);
				mob.resetFallDistance();
			}
			if (s.mode == Mode.NONE && now < s.strafeUntil && s.foe != null && mob.onGround() && now >= s.drivenUntil) {
				strafe(mob, s);
			}
		}
	}

	private static void strafe(Mob mob, State s) {
		LivingEntity foe = s.foe;
		Vec3 to = foe.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
		if (to.lengthSqr() < 1.0E-4) {
			return;
		}
		Vec3 side = new Vec3(-to.z, 0.0, to.x).normalize().scale(s.strafeDir * (STRAFE_SPEED + STRAFE_SPEED_RANK * s.rank));
		Vec3 v = mob.getDeltaMovement();
		mob.setDeltaMovement(v.x * 0.3 + side.x, v.y, v.z * 0.3 + side.z);
		mob.syncVelocity = true;
	}

	/** One tick of flying: a combat circle and dive round the foe, or a trip to the goal. */
	private static void fly(ServerLevel level, Mob mob, State s, long now) {
		Vec3 at = mob.position();
		Vec3 want;
		double speed;
		double cap;
		if (s.mode == Mode.TRAVEL) {
			Entity goal = s.goal;
			if (goal == null || !goal.isAlive() || goal.isRemoved() || goal.level() != mob.level() || now > s.travelUntil) {
				land(mob, s);
				return;
			}
			Vec3 to = goal.position().subtract(at);
			double distance = to.length();
			double rise = Mth.clamp((distance - ARRIVE) * 0.3, 0.0, CRUISE_RISE);
			double ty = goal.getY() + 1.0 + rise;
			want = new Vec3(to.x, ty - at.y, to.z);
			speed = Math.min(TRAVEL_SPEED, 0.15 + distance * 0.03);
			cap = TRAVEL_CLIMB;
			mob.getLookControl().setLookAt(goal, 30.0F, 30.0F);
			face(mob, to);
		} else {
			LivingEntity foe = s.foe;
			if (foe == null || !foe.isAlive()) {
				land(mob, s);
				return;
			}
			Vec3 fc = foe.getBoundingBox().getCenter();
			Vec3 mc = mob.getBoundingBox().getCenter();
			Vec3 to = fc.subtract(mc);
			double distance = to.length();
			double base = FLY_SPEED + FLY_SPEED_RANK * s.rank;
			if (s.engaged && s.phase != Phase.DIVE) {
				s.phase = Phase.HOLD;
				s.phaseEnd = Math.max(s.phaseEnd, now + 10);
			}
			switch (s.phase) {
				case ORBIT -> {
					s.angle += ORBIT_STEP * s.dir;
					Vec3 spot = fc.add(Math.cos(s.angle) * s.radius, ORBIT_HOVER + Math.sin(now * 0.1) * ORBIT_BOB, Math.sin(s.angle) * s.radius);
					want = spot.subtract(mc);
					speed = Math.min(base, want.length() * 0.2 + 0.08);
					if (now >= s.phaseEnd) {
						if (distance <= DIVE_FROM) {
							s.phase = Phase.DIVE;
							s.phaseEnd = now + DIVE_MAX;
						} else {
							s.phaseEnd = now + 20;
						}
					}
				}
				case DIVE -> {
					want = to;
					speed = DIVE_SPEED + DIVE_SPEED_RANK * s.rank;
					if (distance - foe.getBbWidth() * 0.5 <= DIVE_REACH || now >= s.phaseEnd) {
						s.phase = Phase.HOLD;
						s.phaseEnd = now + HOLD_MIN + mob.getRandom().nextInt(HOLD_JITTER);
					}
				}
				default -> {
					double gap = distance - foe.getBbWidth() * 0.5;
					want = gap > HOLD_GAP ? to : Vec3.ZERO;
					speed = HOLD_SPEED;
					if (now >= s.phaseEnd && !s.engaged) {
						s.phase = Phase.ORBIT;
						s.dir = -s.dir;
						s.radius = ORBIT_MIN + mob.getRandom().nextDouble() * (ORBIT_MAX - ORBIT_MIN);
						s.angle = Math.atan2(mc.z - fc.z, mc.x - fc.x);
						s.phaseEnd = now + orbitTicks(mob, s);
					}
				}
			}
			cap = CLIMB + 0.3;
			mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
			face(mob, to);
		}
		Vec3 motion = mob.getDeltaMovement();
		Vec3 goalV = Vec3.ZERO;
		double len = want.length();
		if (len > 1.0E-3) {
			goalV = want.scale(Math.min(speed, len) / len);
			goalV = new Vec3(goalV.x, Mth.clamp(goalV.y, -cap, cap), goalV.z);
		}
		Vec3 v = motion.lerp(goalV, 0.35);
		// check ahead for blocks: over the top, slower
		if (v.lengthSqr() > 1.0E-4) {
			Vec3 ahead = v.normalize().scale(PROBE);
			if (!level.noCollision(mob, mob.getBoundingBox().move(ahead))) {
				v = new Vec3(v.x * 0.4, Math.max(v.y, CLIMB), v.z * 0.4);
			}
		}
		// never under the world floor or over the build limit
		double floor = level.getMinY() + FLOOR_MARGIN, ceiling = level.getMaxY() - CEILING_MARGIN;
		if (at.y < floor) {
			v = new Vec3(v.x, Math.max(v.y, CLIMB), v.z);
		} else if (at.y > ceiling) {
			v = new Vec3(v.x, Math.min(v.y, -CLIMB), v.z);
		}
		mob.setNoGravity(true);
		mob.setDeltaMovement(v);
		mob.syncVelocity = true;
		mob.resetFallDistance();
		mob.getNavigation().stop();
		if (now % TRAIL_EVERY == 0) {
			Vec3 centre = at.add(0.0, mob.getBbHeight() * 0.5, 0.0);
			trail(level, mob, s, centre, 1);
			if (s.mode == Mode.TRAVEL && s.last != null && s.last.distanceToSqr(centre) > 1.0) {
				MagicFx.stroke(level, s.last, centre, 0.3F, s.profile.color());
			}
			s.last = centre;
		}
	}

	private static void face(Mob mob, Vec3 to) {
		if (to.horizontalDistanceSqr() > 1.0E-4) {
			mob.setYRot((float)(Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F);
			mob.yBodyRot = mob.getYRot();
		}
	}

	private static void trail(ServerLevel level, Mob mob, State s, Vec3 at, int count) {
		GiftKit kit = s.profile.kit();
		if (kit != null && mob instanceof PathfinderMob p) {
			for (int i = 0; i < count; i += 3) {
				kit.trail(level, p, at);
			}
			return;
		}
		level.sendParticles(MagicFx.wisp(s.profile.color(), 0.9F), at.x, at.y, at.z, Math.max(2, count), 0.2, 0.3, 0.2, 0.02);
	}

	// ================================================================ vanish dodge

	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof Mob mob) || amount <= 0.0F) {
			return true;
		}
		State s = STATES.get(mob);
		if (s == null || s.rank < MIN_RANK || !(mob.level() instanceof ServerLevel level) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		long now = level.getGameTime();
		if (now - s.seen > STALE || now < s.vanishAt || now < s.drivenUntil || now >= s.openFrom && now < s.openUntil) {
			return true;
		}
		// only a blow, blast or beam from someone: never a fall, fire, the void or a command
		if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == victim || !attacker.isAlive()) {
			return true;
		}
		if (mob.getRandom().nextFloat() >= Mth.lerp(s.rank, VANISH_MIN, VANISH_MAX)) {
			return true;
		}
		return !vanish(level, mob, s, attacker, now);
	}

	/** Blinks {@code mob} behind {@code attacker}; false if there is no room. */
	private static boolean vanish(ServerLevel level, Mob mob, State s, LivingEntity attacker, long now) {
		Vec3 look = attacker.getLookAngle().multiply(1.0, 0.0, 1.0);
		if (look.lengthSqr() < 1.0E-4) {
			look = mob.position().subtract(attacker.position()).multiply(1.0, 0.0, 1.0);
		}
		if (look.lengthSqr() < 1.0E-4) {
			return false;
		}
		Vec3 behind = attacker.position().subtract(look.normalize().scale(BEHIND));
		double y = attacker.getY();
		boolean room = false;
		for (int up = 0; up <= 2 && !room; up++) {
			Vec3 spot = new Vec3(behind.x, y + up, behind.z);
			room = level.noCollision(mob, mob.getBoundingBox().move(spot.x - mob.getX(), spot.y - mob.getY(), spot.z - mob.getZ()));
			if (room) {
				y += up;
			}
		}
		if (!room) {
			return false;
		}
		Vec3 from = mob.position();
		Vec3 to = new Vec3(behind.x, y, behind.z);
		float yaw = (float)(Mth.atan2(attacker.getZ() - to.z, attacker.getX() - to.x) * Mth.RAD_TO_DEG) - 90.0F;
		GiftKit kit = s.profile.kit();
		if (kit != null && mob instanceof PathfinderMob p) {
			kit.vanishFx(level, p, from, to);
		} else {
			blink(level, from, to, s.profile.color());
		}
		mob.teleportTo(level, to.x, to.y, to.z, Set.of(), yaw, 0.0F, false);
		mob.setDeltaMovement(Vec3.ZERO);
		mob.getNavigation().stop();
		s.vanishAt = now + VANISH_GAP + mob.getRandom().nextInt(VANISH_JITTER);
		s.vanished = true;
		s.decideAt = now;
		return true;
	}

	/**
	 * A ki blink: a fading silhouette (a stroke up the body, glints along it) where the fighter stood, a flash where it
	 * appears, and the blink's sound. Powers colour it with {@code color} or draw their own ({@link GiftKit#vanishFx}).
	 */
	public static void blink(ServerLevel level, Vec3 from, Vec3 to, int color) {
		MagicFx.stroke(level, from.add(0.0, 0.1, 0.0), from.add(0.0, 1.8, 0.0), 0.55F, color);
		MagicFx.burst(level, from.x, from.y + 0.6, from.z, color, 1.0F, 8, 0.25, 0.3, 0.25);
		MagicFx.burst(level, from.x, from.y + 1.3, from.z, color, 0.8F, 6, 0.2, 0.2, 0.2);
		MagicFx.burst(level, to.x, to.y + 1.0, to.z, color, 1.2F, 10, 0.3, 0.5, 0.3);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.7F, 1.8F);
	}

	// ================================================================ guard and parry

	/**
	 * What is left of {@code damage} after {@code victim}'s guard, if it is a fighting NPC with one up (or snaps one up as the
	 * blow lands): half from the front, nothing and a parry if the guard is fresh. The victim side of the damage hook
	 * ({@code ZFighterHooks.modifyDamage}); anyone else's damage passes through.
	 */
	public static float guard(LivingEntity victim, DamageSource source, float damage) {
		if (damage <= 0.0F || !(victim instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) {
			return damage;
		}
		State s = STATES.get(mob);
		if (s == null || s.rank < MIN_RANK || level.getGameTime() - s.seen > STALE) {
			return damage;
		}
		boolean melee = source.getDirectEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity();
		boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE);
		if (!melee && !projectile || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
			|| source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE)
			|| !inFront(mob, source.getSourcePosition())) {
			return damage;
		}
		long now = level.getGameTime();
		if (now >= s.openFrom && now < s.openUntil) {
			// caught open by a bitten feint: no guard, and the blow lands harder
			return damage * Feints.OPEN_BONUS;
		}
		if (s.guardStart < 0L && now >= s.guardAt && now >= s.drivenUntil && !s.engaged && mob.getRandom().nextFloat() < SNAP_MAX * s.rank) {
			// a guard snapped up as the blow lands: a fresh one, so a parry
			raise(mob, s, now);
		}
		if (s.guardStart < 0L) {
			return damage;
		}
		LivingEntity attacker = melee ? (LivingEntity)source.getDirectEntity() : source.getEntity() instanceof LivingEntity e ? e : null;
		Vec3 at = mob.getEyePosition().add(mob.getLookAngle().scale(0.6));
		Vec3 line = attacker == null ? null : mob.position().subtract(attacker.position());
		if (now - s.guardStart <= PARRY_TICKS && now >= s.parryAt) {
			s.parryAt = now + PARRY_GAP;
			ClashFx.sparks(level, at, line, ClashFx.PARRY_SPARKS, ClashFx.PARRY_SCALE);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.35F, 2.0F);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.0F, 1.4F);
			if (attacker != null && attacker.isAlive()) {
				Vec3 away = attacker.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
				if (away.lengthSqr() > 1.0E-4) {
					Vec3 v = attacker.getDeltaMovement().add(away.normalize().scale(PARRY_PUSH));
					attacker.setDeltaMovement(v.x, Math.max(v.y, 0.0) + PARRY_LIFT, v.z);
					attacker.syncVelocity = true;
				}
				attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, 3, false, false), mob);
				attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STAGGER_TICKS, 1, false, false), mob);
				s.counterFoe = attacker;
				s.counterAt = now + COUNTER_DELAY;
			}
			return 0.0F;
		}
		level.sendParticles(MagicFx.wisp(s.profile.color(), 1.0F), at.x, at.y - 0.4, at.z, 5, 0.2, 0.2, 0.2, 0.05);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 0.8F, 1.3F);
		if (++s.blows >= GUARD_BREAK) {
			// the guard breaks: sparks, a stumble, and no new guard for a while
			ClashFx.sparks(level, at, line, ClashFx.CLASH_SPARKS, ClashFx.CLASH_SCALE);
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 2, false, false));
			drop(mob, s, now, BREAK_GAP);
		}
		return damage * (1.0F - BLOCK_SHARE);
	}

	/** The counter after a parry: a hard blow back, a beat later. */
	private static void counter(ServerLevel level, Mob mob, State s, long now) {
		if (s.counterFoe == null || now < s.counterAt) {
			return;
		}
		LivingEntity foe = s.counterFoe;
		s.counterFoe = null;
		if (!foe.isAlive() || mob.distanceTo(foe) > GUARD_NEAR) {
			return;
		}
		float blow = mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? (float)mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : COUNTER_FALLBACK;
		foe.setInvulnerableTime(0);
		if (foe.hurtServer(level, level.damageSources().mobAttack(mob), blow * COUNTER_SHARE)) {
			Vec3 at = foe.getBoundingBox().getCenter();
			ClashFx.sparks(level, at, at.subtract(mob.position()), ClashFx.BLOCK_SPARKS, ClashFx.BLOCK_SCALE);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 0.7F);
		}
	}

	private static boolean inFront(Mob mob, @Nullable Vec3 from) {
		if (from == null) {
			return false;
		}
		Vec3 to = from.subtract(mob.position()).multiply(1.0, 0.0, 1.0);
		if (to.lengthSqr() < 1.0E-4) {
			return true;
		}
		Vec3 look = Vec3.directionFromRotation(0.0F, mob.getYRot());
		return to.normalize().dot(look) >= Math.cos(Math.toRadians(FRONT_DEGREES / 2.0));
	}
}
