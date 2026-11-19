package com.jaymospowers.client.rig;

import net.minecraft.client.Minecraft;

/**
 * The back rig's state on the client: which back-rig active the wearer just used and how long its pose has left,
 * mirrored from the move the wearer is midway through ({@link com.jaymospowers.rig.RigMoves}). The Rig menu's "on"
 * strip reads it.
 */
public final class RigBackClient {
	/** The active just picked (a {@link com.jaymospowers.rig.RigBack BK_*} action, or 0 none), and when its pose ends. */
	static int active = 0;
	static int until = 0;

	private RigBackClient() {
	}

	/** Note that the local player just used {@code action} (nothing for the wings toggle, which has no pose). */
	static void note(int action, int ticks) {
		if (ticks <= 0) {
			return;
		}
		active = action;
		until = ticks;
	}

	/** Count the active's pose down, once a client tick. */
	static void tick(Minecraft minecraft) {
		if (until > 0 && --until <= 0) {
			active = 0;
		}
	}

	/** Whether {@code action}'s pose is still playing out. */
	static boolean out(int action) {
		return active == action && until > 0;
	}
}
