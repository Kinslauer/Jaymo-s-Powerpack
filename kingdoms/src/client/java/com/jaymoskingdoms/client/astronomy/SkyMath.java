package com.jaymoskingdoms.client.astronomy;

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
    /** Observer latitude (radians): fixed for the whole world. */
    static final double LATITUDE = Math.toRadians(45.0);
    /** Degrees of sky longitude per block traveled east. */
    static final double LON_DEG_PER_BLOCK = 1.0 / 2000.0;
    /** Degrees the stars shift per in-game day (about one turn per year). */
    static final double DAY_SHIFT_DEG = 0.9856;
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
        double lstDeg = ((time - 6000L + partial) / 24000.0) * 360.0 + lonDeg + days * DAY_SHIFT_DEG;
        double ha = Math.toRadians(lstDeg - star.ra());
        double dec = Math.toRadians(star.dec());
        double alt = Math.asin(Mth.clamp(
                (float) (Math.sin(dec) * Math.sin(LATITUDE) + Math.cos(dec) * Math.cos(LATITUDE) * Math.cos(ha)),
                -1.0F, 1.0F));
        double az = Math.atan2(Math.sin(ha), Math.cos(ha) * Math.sin(LATITUDE) - Math.tan(dec) * Math.cos(LATITUDE));
        double h = Math.cos(alt);
        return new Vec3(-h * Math.sin(az), Math.sin(alt), h * Math.cos(az));
    }
}
