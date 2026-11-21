package com.AdamNew.dynamicwaters;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;

/**
 * Whether land is there to read without waiting. In 26.3 {@code hasChunkAt} is also true of a chunk that is only scheduled
 * and still being made, and reading it then makes the server thread wait for it. On the server's own thread this answers
 * only for a chunk that is made and loaded; anywhere else (world generation, other threads) it is {@code hasChunkAt}.
 */
public final class Loaded {
    private Loaded() {
    }

    public static boolean made(LevelReader level, BlockPos pos) {
        return made(level, pos.getX(), pos.getZ());
    }

    public static boolean made(LevelReader level, int x, int z) {
        if (level instanceof ServerLevel server && server.getServer().isSameThread()) {
            return server.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
        }
        return level.hasChunkAt(x, z);
    }
}
