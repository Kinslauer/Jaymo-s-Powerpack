package com.jaymoskingdoms.astronomy;

/**
 * The sky's model, shared by the client's sky ({@code SkyMath}, {@code SkyClient}) and the server's readings
 * ({@link Measurements}), so what a player sees and what their journal records come from the same numbers.
 * <p>
 * A star's catalogue place (right ascension, declination) becomes an altitude and azimuth for an observer at a fixed
 * latitude, at a sky longitude taken from how far east they stand, at a local sidereal time worked out from the world's
 * clock and day count. The Astronomer's Journal's study pages explain each step and where it parts from the real sky.
 */
public final class Celestial {
	/** The observer's latitude (degrees): the same everywhere in the world. */
	public static final double LATITUDE_DEG = 45.0;
	/** Degrees of sky longitude per block travelled east. */
	public static final double LON_DEG_PER_BLOCK = 1.0 / 2000.0;
	/** Degrees the stars shift per in-game day (one turn in about 365 days). */
	public static final double DAY_SHIFT_DEG = 0.9856;
	/** Degrees of right ascension per hour (the catalogue gives right ascension in hours). */
	public static final double DEG_PER_HOUR = 15.0;
	/** Ticks in a world day, and the time of day the sidereal clock reads 0 at longitude 0 on day 0 (noon). */
	public static final long DAY_TICKS = 24000L, LST_ZERO_TICKS = 6000L;

	private Celestial() {
	}

	/**
	 * Local sidereal time (degrees, not wrapped): the right ascension on the meridian now. {@code clockTime} is the
	 * world's time of day (0-23999), {@code partial} the part of a tick, {@code lonDeg} the observer's sky longitude and
	 * {@code days} the day count.
	 */
	public static double lstDeg(long clockTime, float partial, double lonDeg, int days) {
		return ((clockTime - LST_ZERO_TICKS + partial) / (double) DAY_TICKS) * 360.0 + lonDeg + days * DAY_SHIFT_DEG;
	}

	/** {@code blockX}'s sky longitude (degrees east). */
	public static double longitude(double blockX) {
		return blockX * LON_DEG_PER_BLOCK;
	}

	/** Altitude above the horizon (radians) of a star at {@code raDeg}, {@code decDeg} when the sidereal time is {@code lstDeg}. */
	public static double altitude(double raDeg, double decDeg, double lstDeg) {
		double ha = Math.toRadians(lstDeg - raDeg), dec = Math.toRadians(decDeg), lat = Math.toRadians(LATITUDE_DEG);
		double s = Math.sin(dec) * Math.sin(lat) + Math.cos(dec) * Math.cos(lat) * Math.cos(ha);
		return Math.asin(Math.max(-1.0, Math.min(1.0, s)));
	}

	/**
	 * Azimuth (radians) of the same star, measured from south toward west (the astronomers' old convention, which the
	 * world direction in {@code SkyMath} uses); add pi for the compass bearing from north through east.
	 */
	public static double azimuthFromSouth(double raDeg, double decDeg, double lstDeg) {
		double ha = Math.toRadians(lstDeg - raDeg), dec = Math.toRadians(decDeg), lat = Math.toRadians(LATITUDE_DEG);
		return Math.atan2(Math.sin(ha), Math.cos(ha) * Math.sin(lat) - Math.tan(dec) * Math.cos(lat));
	}

	/** The compass bearing (degrees from north through east, 0-360) for an azimuth from south ({@link #azimuthFromSouth}). */
	public static double bearingDeg(double azimuthFromSouth) {
		double deg = Math.toDegrees(azimuthFromSouth) + 180.0;
		return ((deg % 360.0) + 360.0) % 360.0;
	}
}
