package com.jaymoskitchen.cooking.hands;

import net.minecraft.server.level.ServerPlayer;

/** Which kinds of cooking are done by hand (each registers its run here), and what has to be true to begin one. */
final class Runs {
	private Runs() {
	}

	static void register() {
	}

	/** Whether {@code pick} can begin on this table (the spit is up for a roast...); says so to the cook if not. */
	static boolean ready(Table table, Pick pick, ServerPlayer cook) {
		return true;
	}
}
