package com.jaymoskitchen.cooking;

/**
 * The joint-rolling minigame on the prep board: grind the Pmeh (circle the mouse around the grinder), sprinkle it
 * evenly along the paper (hold the mouse where it should land), then roll it (drag up and down at a steady pace;
 * too fast tears the paper). Played on the client, which reports how it went; both sides judge with {@link #score}.
 */
public final class Rolling {
	/** Grinder turns needed (random each time, in this range) and the time allowed. */
	public static final int GRIND_TURNS_MIN = 5, GRIND_TURNS_MAX = 8, GRIND_TICKS = 200;
	/** The paper is judged in this many stretches; the herb runs out after SUPPLY. */
	public static final int SEGMENTS = 8;
	public static final float SUPPLY = 1.0F;
	/** Herb sprinkled per tick while held (random per roll, in this range). */
	public static final float SPRINKLE_RATE_MIN = 0.009F, SPRINKLE_RATE_MAX = 0.014F;
	/** Rolling: distance (in GUI pixels) to roll it up, the slowest pace that counts, and the time allowed. */
	public static final float ROLL_LENGTH = 260.0F, ROLL_MIN_SPEED = 1.5F;
	/** Faster than this (pixels per tick; random per roll, in this range) tears the paper. */
	public static final float ROLL_MAX_SPEED_MIN = 7.0F, ROLL_MAX_SPEED_MAX = 10.0F;
	public static final int ROLL_TICKS = 240;
	/** Tear damage that rips the paper outright, and the most a Perfect roll may have. */
	public static final float TEAR_LIMIT = 30.0F, PERFECT_TEARS = 5.0F;
	/** A (non-perfect) joint with at least this much tearing is a Torn Joint: its puffs only do bad things. */
	public static final float TORN_TEARS = 0.5F;
	/** Evenness (0..1) needed for the joint to hold together, and for a Perfect. */
	public static final float EVEN_ENOUGH = 0.6F, PERFECT_EVENNESS = 0.82F;
	/** A real attempt takes at least this long. */
	static final int MIN_TICKS = 60;

	private Rolling() {
	}

	/**
	 * How evenly the herb was spread: 1 when every stretch of paper got an equal share of the whole supply,
	 * lower as it piles up in places, leaves gaps, or misses the paper.
	 */
	public static float evenness(float[] segments) {
		float target = SUPPLY / SEGMENTS;
		float off = 0.0F;
		for (float amount : segments) {
			off += Math.abs(amount - target);
		}
		return Math.max(0.0F, 1.0F - off / (2.0F * SUPPLY));
	}

	public static Prep.Result score(boolean ground, float evenness, float tears, boolean rolled) {
		if (!ground || !rolled || tears >= TEAR_LIMIT || evenness < EVEN_ENOUGH) {
			return Prep.Result.RUINED;
		}
		return evenness >= PERFECT_EVENNESS && tears <= PERFECT_TEARS ? Prep.Result.PERFECT : Prep.Result.GOOD;
	}
}
