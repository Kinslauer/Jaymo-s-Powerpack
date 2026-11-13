package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;

/**
 * Whether land is there to read without waiting. In 26.3 {@code Level.isLoaded} and {@code hasChunkAt} are also true of a
 * chunk that is only scheduled (its holder at the full ticket level) and still being made; reading it then waits on the
 * server thread for it to generate, and on the Steam Deck that ran past the 60 s watchdog again and again (crashes
 * 2026-11-13, worst round fresh land such as Mohknee's). On a server level these answer only for a chunk that is made and
 * loaded; anywhere else (the client, world generation) they're the game's own checks, unchanged.
 */
public final class Loaded {
	private Loaded() {
	}

	/** {@code pos}'s chunk is made and loaded (a server level), else {@code level}'s own isLoaded / hasChunkAt. */
	public static boolean made(LevelReader level, BlockPos pos) {
		if (level instanceof ServerLevel server) {
			return server.isInValidBounds(pos) && server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
		}
		return level instanceof Level plain ? plain.isLoaded(pos) : level.hasChunkAt(pos);
	}

	/** The block column {@code x, z}'s chunk is made and loaded (a server level), else {@code level}'s own hasChunkAt. */
	public static boolean made(LevelReader level, int x, int z) {
		if (level instanceof ServerLevel server) {
			return server.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
		}
		return level.hasChunkAt(x, z);
	}
}
