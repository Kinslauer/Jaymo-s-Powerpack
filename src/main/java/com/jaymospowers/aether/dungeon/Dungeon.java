package com.jaymospowers.aether.dungeon;

/**
 * The Aether's dungeon (docs/aether.md, "Sentry Ruins and the Slider"): the sentry blocks, the mobs and the structure. {@link #init()}
 * is called by {@code Aether.init()}.
 */
public final class Dungeon {
	private Dungeon() {
	}

	public static void init() {
		DungeonBlocks.init();
		DungeonMobs.init();
		SentryRuins.init();
	}
}
