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
			case FRONT_FLIP, CARTWHEEL, LAND_ROLL -> 14;
			case CORKSCREW, GIANT -> 16;
			case TWIRL -> 12;
			case HERO_LAND -> 28;
			case WINGS_LAND -> 20;
			case ROAR -> WebSymbiote.ROAR_TICKS;
			case SPIKES -> WebSymbiote.SPIKE_POSE_TICKS;
			default -> DODGE_TICKS;
		};
	}

	public static boolean trick(int kind) {
		return kind >= FIRST_TRICK && kind <= LAST_TRICK;
	}

	public static boolean landing(int kind) {
		return kind == HERO_LAND || kind == LAND_ROLL || kind == WINGS_LAND;
	}

	/** How far through it is at {@code time} (game time + partial tick), 0-1; past 1 it's over. */
	public float progress(float time) {
		return (time - start) / ticks();
	}
}
