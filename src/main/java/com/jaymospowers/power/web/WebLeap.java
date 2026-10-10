package com.jaymospowers.power.web;

import net.minecraft.util.Mth;

/**
 * The suit's charged leap, as numbers both sides share: how hard a charge throws you up, how high that carries you, and
 * how much charge a ledge needs. The leap itself is the client's (see {@code WebSwing#tickChargeJump}); a takeover
 * coils it too, for ledges taller than a step (see {@link WebTakeoverPath}, {@code WebSymbioteClient}).
 */
public final class WebLeap {
	/** Ticks to a full charge; the leap from a tap to a full charge (up speed, and forward along the look), blocks/tick. */
	public static final int CHARGE_FULL = 30;
	public static final double CHARGE_UP_MIN = 0.45, CHARGE_UP_MAX = 1.65;
	public static final double CHARGE_FORWARD_MAX = 1.1;
	/** A takeover never coils more than this share of a full charge. */
	public static final float TAKEOVER_MAX_CHARGE = 0.75F;
	/** It charges to clear the ledge by this much (blocks) at the top of the leap. */
	public static final double CLEARANCE = 0.6;
	/** Vanilla's fall: gravity (blocks/tick², the attribute's default) and air drag per tick. */
	public static final double GRAVITY = 0.08, DRAG = 0.98;
	/** Ticks the flight is simulated for (far past any apex). */
	private static final int SIMULATE_TICKS = 120;
	/** Steps of the search for the charge a height needs. */
	private static final int SEARCH_STEPS = 16;
	/**
	 * The tallest rise (blocks, a whole number) a takeover's leap may climb: what {@link #TAKEOVER_MAX_CHARGE} clears
	 * with {@link #CLEARANCE} to spare, at normal gravity.
	 */
	public static final int TAKEOVER_MAX_RISE = (int)Math.floor(apex(speed(TAKEOVER_MAX_CHARGE), GRAVITY) - CLEARANCE);

	private WebLeap() {
	}

	/** The upward speed a charge (0-1) leaps with. */
	public static double speed(float charge) {
		return Mth.lerp(Mth.clamp(charge, 0.0F, 1.0F), CHARGE_UP_MIN, CHARGE_UP_MAX);
	}

	/** How high (blocks) a leap at {@code up} blocks/tick rises, under {@code gravity}: the player's own fall, tick by tick. */
	public static double apex(double up, double gravity) {
		double y = 0.0, top = 0.0, v = up;
		for (int i = 0; i < SIMULATE_TICKS && v > 0.0; i++) {
			y += v;
			top = Math.max(top, y);
			v = (v - gravity) * DRAG;
		}
		return top;
	}

	/**
	 * The least charge (0-{@code max}) that clears a rise of {@code rise} blocks with {@link #CLEARANCE} to spare; {@code max}
	 * if even that falls short.
	 */
	public static float chargeFor(double rise, double gravity, float max) {
		double need = rise + CLEARANCE;
		if (apex(speed(max), gravity) <= need) {
			return max;
		}
		float lo = 0.0F, hi = max;
		for (int i = 0; i < SEARCH_STEPS; i++) {
			float mid = (lo + hi) * 0.5F;
			if (apex(speed(mid), gravity) >= need) {
				hi = mid;
			} else {
				lo = mid;
			}
		}
		return hi;
	}
}
