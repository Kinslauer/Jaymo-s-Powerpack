package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;

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

	/**
	 * Every chunk under the block columns {@code minX..maxX, minZ..maxZ} is made and loaded (a server level), else
	 * {@code level}'s own hasChunksAt. Use it in place of hasChunksAt, which in 26.3 is also true of chunks only scheduled.
	 */
	public static boolean made(LevelReader level, int minX, int minZ, int maxX, int maxZ) {
		if (level instanceof ServerLevel server) {
			for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
				for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
					if (server.getChunkSource().getChunkNow(cx, cz) == null) {
						return false;
					}
				}
			}
			return true;
		}
		return level.hasChunksAt(minX, minZ, maxX, maxZ);
	}

	/** {@link #made(LevelReader, int, int, int, int)} over the columns between {@code a} and {@code b} (heights unused). */
	public static boolean made(LevelReader level, BlockPos a, BlockPos b) {
		return made(level, Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ()));
	}

	/**
	 * {@code level.getHeightmapPos(type, pos)} when {@code pos}'s chunk is made and loaded; else {@code pos} itself, unread
	 * (a {@link #made} check on it after then turns it down), so finding the ground never waits on land being made.
	 */
	public static BlockPos top(LevelReader level, Heightmap.Types type, BlockPos pos) {
		return made(level, pos) ? level.getHeightmapPos(type, pos) : pos;
	}
}
