package com.jaymoskingdoms.client.astronomy;

import com.jaymoskingdoms.astronomy.Celestial;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Where a star is up: equatorial coordinates (right ascension, declination) to
 * a direction in the world, for the world's time of day, the date (the day
 * count shifts the sky through the year) and the observer's position
 * (longitude faked from block position, latitude fixed). The world has no real
 * globe, so "where you stand" shifts the sky by longitude and "when" shifts it
 * by time of day and day count.
 */
public final class SkyMath {
    /** Degrees of sky longitude per block traveled east ({@link Celestial}, shared with the server's readings). */
    static final double LON_DEG_PER_BLOCK = Celestial.LON_DEG_PER_BLOCK;
    /** The sun is overhead at turn 0, under the world at pi. */
    static final float DUSK_AT = 0.05F;
    static final float DUSK_SPAN = 0.25F;

    private SkyMath() {
    }

    /** How dark the sky is (0: day, 1: night), dimmed by rain. */
    public static float night(ClientLevel level, float partial) {
        long time = Math.floorMod(level.getOverworldClockTime(), 24000L);
        double turn = Math.PI * 2.0 * ((time - 6000L + partial) / 24000.0);
        double below = -Math.cos(turn);
        return Mth.clamp((float) ((below - DUSK_AT) / DUSK_SPAN), 0.0F, 1.0F) * (1.0F - level.getRainLevel(partial));
    }

    /**
     * The unit direction to {@code star} now (y up). {@code lonDeg} is the
     * observer's sky-longitude and {@code days} the elapsed in-game day count.
     */
    public static Vec3 direction(Star star, long time, float partial, double lonDeg, int days) {
        double lstDeg = Celestial.lstDeg(time, partial, lonDeg, days);
        double alt = Celestial.altitude(star.ra(), star.dec(), lstDeg);
        double az = Celestial.azimuthFromSouth(star.ra(), star.dec(), lstDeg);
        double h = Math.cos(alt);
        return new Vec3(-h * Math.sin(az), Math.sin(alt), h * Math.cos(az));
    }
}
