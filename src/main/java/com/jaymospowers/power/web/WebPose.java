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

	/** How long each kind lasts (ticks). */
	public static final int DODGE_TICKS = 12;
	public static final int RAGDOLL_TICKS = 40;

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
			case DIVE_KICK -> 40;
			case ROAR -> WebSymbiote.ROAR_TICKS;
			case SPIKES -> WebSymbiote.SPIKE_POSE_TICKS;
			default -> DODGE_TICKS;
		};
	}

	public static boolean trick(int kind) {
		return kind >= FIRST_TRICK && kind <= LAST_TRICK || kind == BACK_FLIP || acro(kind);
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
