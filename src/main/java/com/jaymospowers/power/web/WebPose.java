package com.jaymospowers.power.web;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A whole-body move a web slinger is making right now, for everyone to draw ({@link WebPower#POSE}): one of
 * Spider-Sense's dodges, or a ragdoll after a slingshot's floor gives way. {@code start} is the game time it began;
 * {@code side} is +1 or -1 (which way a sidestep, roll or weave goes).
 */
public record WebPose(int kind, long start, int side) {
	/** Spider-Sense dodges: a spinning sidestep, a backflip into a crouch, a duck and roll, a weave. */
	public static final int SPIN = 0, BACKFLIP = 1, ROLL = 2, WEAVE = 3;
	public static final int DODGES = 4;
	/** Limp: the lines snapped under you and you're down for a moment ({@link #RAGDOLL_TICKS}). */
	public static final int RAGDOLL = 4;

	/** How long each kind lasts (ticks). */
	public static final int DODGE_TICKS = 12;
	public static final int RAGDOLL_TICKS = 40;

	public static final StreamCodec<ByteBuf, WebPose> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, WebPose::kind,
		ByteBufCodecs.VAR_LONG, WebPose::start,
		ByteBufCodecs.VAR_INT, WebPose::side,
		WebPose::new);

	public int ticks() {
		return kind == RAGDOLL ? RAGDOLL_TICKS : DODGE_TICKS;
	}

	/** How far through it is at {@code time} (game time + partial tick), 0-1; past 1 it's over. */
	public float progress(float time) {
		return (time - start) / ticks();
	}
}
