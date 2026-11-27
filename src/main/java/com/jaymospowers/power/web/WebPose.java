package com.jaymospowers.power.web;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A whole-body move a web slinger is making right now, for everyone to draw ({@link WebPower#POSE}): one of
 * Spider-Sense's dodges, a ragdoll after a slingshot's floor gives way, or a swing trick ({@link WebTricks}). {@code start} is the game time it began;
 * {@code side} is +1 or -1 (which way a sidestep, roll or weave goes).
 */
public record WebPose(int kind, long start, int side) {
	/** Spider-Sense dodges: a spinning sidestep, a backflip into a crouch, a duck and roll, a weave. */
	public static final int SPIN = 0, BACKFLIP = 1, ROLL = 2, WEAVE = 3;
	public static final int DODGES = 4;
	/** Limp: the lines snapped under you and you're down for a moment ({@link #RAGDOLL_TICKS}). */
	public static final int RAGDOLL = 4;
	/**
	 * Swing tricks, asked for by the slinger's own client ({@link WebTricks}). Off a released web: a tucked front
	 * flip, a laid-out corkscrew, a cartwheel. Still on the web: a twirl around the line, a giant swing over the hand.
	 * Touching down: a three-point superhero landing, a forward roll, and the wings' flare and spin.
	 */
	public static final int FRONT_FLIP = 5, CORKSCREW = 6, CARTWHEEL = 7, TWIRL = 8, GIANT = 9,
		HERO_LAND = 10, LAND_ROLL = 11, WINGS_LAND = 12;
	public static final int FIRST_TRICK = FRONT_FLIP, LAST_TRICK = WINGS_LAND;
	/**
	 * The symbiote's roar ({@link WebSymbiote#ROAR_TICKS}): hunched and shuddering while the suit pieces itself
	 * together over you, then thrown back, arms flung wide, as it roars.
	 */
	public static final int ROAR = 13;
	/**
	 * Spike Burst ({@link WebSymbiote#SPIKE_POSE_TICKS}): curled in, gathering ({@link WebSymbiote#SPIKE_GATHER_TICKS}),
	 * then thrown open as spikes and lashing tendrils burst out of the whole body.
	 */
	public static final int SPIKES = 14;
	/**
	 * (Motion patch 0) A tucked flip over backwards in mid-air, from a tap of Sneak in the air with Back held ({@code
	 * freerun.AirFlip}): anyone who may flip, web slinger or freerunner. A trick like the others (its own number, past
	 * the symbiote's two, so nothing saved moves).
	 */
	public static final int BACK_FLIP = 15;
	/** Glide kick ({@link WebGlideKick}): both feet thrown out ahead, body laid back, arms swept behind. */
	public static final int GLIDE_KICK = 16;
	/** Hanging head down from a line above ({@link WebHanging}); lasts as long as the attachment {@link WebPower#UPSIDE} does, not a set time. */
	public static final int UPSIDE = 17;
	/** A balanced crouch on a narrow top ({@link WebPerch}); lasts as long as the attachment {@link WebPower#PERCH} does. */
	public static final int PERCH = 18;
	/**
	 * Acrobatics ({@code freerun.moves.Acrobatics}): the standing backflip off the dodge key with Back held (the Backflip
	 * perk), then the dodge key's mid-air tricks: a tucked side flip, a tornado spin, a toe touch, a swan, a straight back
	 * flip with a full twist, a double front, a double back and a butterfly twist. Numbered past the perch, so nothing saved moves.
	 */
	public static final int STAND_BACKFLIP = 19, SIDE_FLIP = 20, AIR_SPIN = 21, TOE_TOUCH = 22, SWAN = 23, LAYOUT_TWIST = 24,
		DOUBLE_FRONT = 25, DOUBLE_BACK = 26, BUTTERFLY = 27;
	public static final int FIRST_ACRO = STAND_BACKFLIP, LAST_ACRO = BUTTERFLY;
	/**
	 * Strikes out of a swing ({@link WebAirStrikes}), thrown in the air after a web is let go: a rising blow that sends
	 * the foe up, a two-footed dive kick that slams the ground where it lands, and a spinning strike that hits all round.
	 * Not tricks, so nothing cuts them short and they hold off the next trick while they play.
	 */
	public static final int RISING_BLOW = 28, DIVE_KICK = 29, SPIN_STRIKE = 30;
	/**
	 * More ways off a released web, the loop over an anchor and the barrel roll (Legends patch 1, our own hand-made poses):
	 * a swan dive laid out with the arms wide, a pike (legs straight, folded at the hips) over forwards, a tucked
	 * sideways roll, a back flip with a half twist, and a handspring over the hands. {@link #LOOP_FLIP} is the whole body
	 * going round over the hand on a swing that loops its anchor ({@code WebLoop}); {@link #DIVE_ROLL} is one turn of the
	 * barrel roll of a dive (sent again, with a side of 2 or -2, to keep it going). All of them are tricks, numbered past
	 * the strikes so nothing saved moves.
	 */
	public static final int SWAN_DIVE = 31, PIKE_FLIP = 32, SIDE_ROLL = 33, TWIST_FLIP = 34, HANDSPRING = 35, DIVE_ROLL = 36, LOOP_FLIP = 37;
	public static final int FIRST_EXTRA_TRICK = SWAN_DIVE, LAST_EXTRA_TRICK = LOOP_FLIP;
	/**
	 * Hanging by the hands under a webline ({@link WebLineRide}); not a trick: it lasts as long as the attachment
	 * {@link WebLineRide#RIDE} says so.
	 */
	public static final int LINE_HANG = 38;
	/**
	 * Web attacks (Legends patch 2, our own hand-made poses), all started by the server and none of them tricks, numbered past
	 * the webline hang so nothing saved moves. {@link #ZIP_KICK}: flying feet first down a zip line at a webbed foe, the
	 * kick landing on arrival ({@link WebZipStrike}). {@link #SWING_UP_KICK}, {@link #PENDULUM_KICK} and {@link #WEB_YANK_UP}:
	 * the web air launchers ({@link WebUptilt}). {@link #DIVE_SLAM}: both lines in the ground, hauled down head first
	 * ({@link WebDiveSlam}). {@link #PIN_THROW}: a foe yanked sideways onto a wall ({@link WebPin}). {@link #DROP_KICK}: the
	 * flip into a drop kick ({@link WebRisingKick}). {@link #SHOCK_HOLD}: braced with both lines taut ({@link WebShock}).
	 * {@link #WRECK_SWING}: whirling a ball of rubble on both lines ({@link WebWreck}). {@link #WEB_STORM}: arms spinning out a
	 * dome of lines ({@link WebStorm}). {@link #FIST_JAB} to {@link #FIST_CRUSH}: the five webbed fist blows
	 * ({@link WebFistCombo}). {@link #SENSE_TURN}: spinning round to answer an arrow ({@link WebZipStrike#answer}).
	 * {@link #HUNT_DIVE}: one pass of the hunting dive ({@link WebHunt}). {@link #SURF}: standing on a launched foe's back
	 * ({@link WebSurf}).
	 */
	public static final int ZIP_KICK = 39, SWING_UP_KICK = 40, PENDULUM_KICK = 41, WEB_YANK_UP = 42, DIVE_SLAM = 43, PIN_THROW = 44,
		DROP_KICK = 45, SHOCK_HOLD = 46, WRECK_SWING = 47, WEB_STORM = 48, FIST_JAB = 49, FIST_KICK = 50, FIST_UPPER = 51, FIST_TWIST = 52,
		FIST_CRUSH = 53, SENSE_TURN = 54, HUNT_DIVE = 55, SURF = 56;
	public static final int FIRST_ATTACK = ZIP_KICK, LAST_ATTACK = SURF;

	/** The big suit's own attacks, recovery and wall motions. */
	public static final int BRUTE_CLAP = 57, BRUTE_FLIP = 58, BRUTE_POUND = 59, BRUTE_RAMPAGE = 60,
		BRUTE_STOMP = 61, BRUTE_NOVA = 62, BRUTE_COIL = 63, BRUTE_AIR = 64, BRUTE_LAND = 65,
		BRUTE_HOOK = 66, BRUTE_HOOK_LUNGE = 67, BRUTE_WALL_GRAB = 68, BRUTE_WALL_HOLD = 69,
		BRUTE_WALL_HEAVE = 70, BRUTE_WALL_SIDE = 71, BRUTE_WALL_LEAP = 72, BRUTE_WALL_LEDGE = 73;

	public static boolean brute(int kind) {
		return kind >= BRUTE_CLAP && kind <= BRUTE_WALL_LEDGE;
	}

	/** How long each kind lasts (ticks). */
	public static final int DODGE_TICKS = 12;
	public static final int RAGDOLL_TICKS = 40;
	/** The loop over the anchor lasts as long as the loop does, between these (ticks). */
	public static final int LOOP_MIN_TICKS = 20, LOOP_MAX_TICKS = 140;

	public static final StreamCodec<ByteBuf, WebPose> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, WebPose::kind,
		ByteBufCodecs.VAR_LONG, WebPose::start,
		ByteBufCodecs.VAR_INT, WebPose::side,
		WebPose::new);

	public int ticks() {
		return switch (kind) {
			case RAGDOLL -> RAGDOLL_TICKS;
			case FRONT_FLIP, BACK_FLIP, CARTWHEEL, LAND_ROLL -> 14;
			case CORKSCREW, GIANT -> 16;
			case TWIRL -> 12;
			case GLIDE_KICK -> 16;
			case HERO_LAND -> 28;
			case WINGS_LAND -> 20;
			case STAND_BACKFLIP, LAYOUT_TWIST -> 18;
			case SIDE_FLIP, SWAN -> 14;
			case AIR_SPIN, TOE_TOUCH -> 10;
			case DOUBLE_FRONT, DOUBLE_BACK -> 22;
			case BUTTERFLY -> 16;
			case RISING_BLOW, SPIN_STRIKE -> 14;
			case SWAN_DIVE, TWIST_FLIP -> 18;
			case PIKE_FLIP, SIDE_ROLL -> 14;
			case HANDSPRING, DIVE_ROLL -> 16;
			// (the loop says how long it will take, in the side; see WebTricks)
			case LOOP_FLIP -> Math.max(LOOP_MIN_TICKS, Math.min(LOOP_MAX_TICKS, Math.abs(side)));
			case DIVE_KICK -> 40;
			case ZIP_KICK, PENDULUM_KICK, SWING_UP_KICK -> 20;
			case WEB_YANK_UP, SENSE_TURN -> 16;
			case DIVE_SLAM -> 100;
			case PIN_THROW -> 12;
			case DROP_KICK -> 22;
			case SHOCK_HOLD -> 44;
			case WRECK_SWING -> 130;
			case WEB_STORM -> 30;
			case FIST_JAB, FIST_KICK, FIST_UPPER, FIST_TWIST, FIST_CRUSH -> 8;
			case HUNT_DIVE -> 24;
			case SURF -> 70;
			case BRUTE_CLAP -> 38;
			case BRUTE_FLIP -> 24;
			case BRUTE_POUND -> WebBruteArts.POUND_TICKS;
			case BRUTE_RAMPAGE, BRUTE_AIR -> 46;
			case BRUTE_STOMP -> 22;
			case BRUTE_NOVA -> WebBruteArts.NOVA_WINDUP + 1;
			case BRUTE_COIL -> WebLeap.CHARGE_FULL;
			case BRUTE_LAND, BRUTE_HOOK, BRUTE_WALL_LEAP -> 12;
			case BRUTE_HOOK_LUNGE, BRUTE_WALL_LEDGE -> 17;
			case BRUTE_WALL_GRAB, BRUTE_WALL_HOLD, BRUTE_WALL_HEAVE, BRUTE_WALL_SIDE -> 9;
			case ROAR -> WebSymbiote.ROAR_TICKS;
			case SPIKES -> WebSymbiote.SPIKE_POSE_TICKS;
			default -> DODGE_TICKS;
		};
	}

	public static boolean trick(int kind) {
		return kind >= FIRST_TRICK && kind <= LAST_TRICK || kind == BACK_FLIP || acro(kind) || kind >= FIRST_EXTRA_TRICK && kind <= LAST_EXTRA_TRICK;
	}

	/** The acrobatics' own kinds: the standing backflip and the mid-air tricks that are new with it. */
	public static boolean acro(int kind) {
		return kind >= FIRST_ACRO && kind <= LAST_ACRO;
	}

	/** How long {@code kind} lasts (ticks). */
	public static int ticks(int kind) {
		return new WebPose(kind, 0L, 1).ticks();
	}

	public static boolean landing(int kind) {
		return kind == HERO_LAND || kind == LAND_ROLL || kind == WINGS_LAND;
	}

	/** How far through it is at {@code time} (game time + partial tick), 0-1; past 1 it's over. */
	public float progress(float time) {
		return (time - start) / ticks();
	}
}
