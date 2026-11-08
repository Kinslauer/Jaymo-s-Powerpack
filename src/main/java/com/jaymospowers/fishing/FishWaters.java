package com.jaymospowers.fishing;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * (0.21.64, Andy: "The goal is more realistic fishing") When fish are keen: what bites, and how many there are, is up to
 * the real fish in the water ({@link FishBites}); this is only how ready they are to notice the bait. They feed more at
 * dawn and dusk and in the rain (ideas from Au Naturel, public domain, and Reel to Fish; nothing copied).
 */
public final class FishWaters {
    /** Fish notice bait this many times as often at dawn and dusk, and in the rain. */
    static final float DAWN_DUSK_BITES = 1.6F;
    static final float RAIN_BITES = 1.3F;
    /** Dawn and dusk, in the day's ticks (0 is sunrise, 12000 sunset). */
    static final int DAWN_FROM = 23000, DAWN_TO = 1500, DUSK_FROM = 11000, DUSK_TO = 13500;

    private FishWaters() {
    }

    /** How much keener (1 = usual) fish at {@code pos} are just now: dawn, dusk and rain. */
    public static float bites(ServerLevel level, BlockPos pos) {
        float f = 1.0F;
        long day = Math.floorMod(level.getOverworldClockTime(), 24000L);
        if (day >= DAWN_FROM || day < DAWN_TO || day >= DUSK_FROM && day < DUSK_TO) {
            f *= DAWN_DUSK_BITES;
        }
        if (level.isRainingAt(pos.above())) {
            f *= RAIN_BITES;
        }
        return f;
    }
}
