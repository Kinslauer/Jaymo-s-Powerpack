package com.jaymoskingdoms.beasts;

import org.jspecify.annotations.Nullable;

/**
 * (0.7.327) A creature drawn from a Blockbench model with its own animations (the client's {@code BbMobRenderer}): the
 * move it's making now, if any, by its animation's name, and how long it has been making it. Between moves the
 * renderer plays its idle and walk.
 */
public interface Acting {
	/** The animation of the move it's making, or null between moves. */
	@Nullable String action();

	/** (Client) how long its move has run (ticks). */
	int actionAge();
}
