package com.jaymospowers.client.freerun.moves;

import com.jaymospowers.freerun.moves.CoyoteTime;
import net.minecraft.client.player.LocalPlayer;

/**
 * Coyote time on this side ({@link CoyoteTime}): counts your own body's ticks off the ground since it last stood, whether
 * that run of air began with a jump, and whether Jump is a fresh press. Asked each tick by {@code CoyoteTimeMixin}.
 */
public final class CoyoteTimeClient {
	/** Ticks since your feet left the ground (0 on it), whether this run of air was a jump (or a late one was taken). */
	private static int off;
	private static boolean jumped, jumpWas;
	/** The tick the last late jump was taken in (see {@link #justTook}). */
	private static int tookAt = Integer.MIN_VALUE;

	private CoyoteTimeClient() {
	}

	/** Your body jumped (from the ground, or a late jump). */
	public static void jumped() {
		jumped = true;
	}

	/** From the jump check in your body's tick: whether a late jump goes now (Jump {@code jumping} held as the game reads it). */
	public static boolean lateJump(LocalPlayer player, boolean jumping) {
		boolean fresh = jumping && !jumpWas;
		jumpWas = jumping;
		if (player.onGround()) {
			off = 0;
			jumped = false;
			return false;
		}
		off++;
		return fresh && !jumped && off <= CoyoteTime.GRACE_TICKS && player.getDeltaMovement().y <= 0.0 && CoyoteTime.allowed(player);
	}

	/** A late jump was taken this tick. */
	public static void took(LocalPlayer player) {
		jumped = true;
		tookAt = player.tickCount;
	}

	/**
	 * Whether a late jump was taken this very tick: that press was the ground's jump, so the air's own jumps (the cloud in a
	 * bottle, a power's second jump) wait for the next press.
	 */
	public static boolean justTook(LocalPlayer player) {
		return tookAt == player.tickCount;
	}
}
