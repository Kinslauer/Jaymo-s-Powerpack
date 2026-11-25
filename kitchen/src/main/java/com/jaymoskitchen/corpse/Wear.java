package com.jaymoskitchen.corpse;

/**
 * How a body has weathered, packed into one synced number ({@link CorpseEntity#wear}) so clients can paint it: charred
 * by fire, blotched dark and then bare to the bone by rot, greyed by age, sunk into the ground, frozen into an ice
 * statue, squashed flat, or fading away. Painted levels run 0 (none) to {@link #LEVELS}.
 */
public final class Wear {
	/** The most any painted stage goes to (0 is none). */
	public static final int LEVELS = 4;
	/** Where each field sits in the number: three bits each, except the sink (seven, a percentage) and the flags (one). */
	public static final int CHAR = 0, DARK = 3, BARE = 6, GREY = 9, FROZEN_BIT = 12, FADING_BIT = 13, FADE = 14, CRUSHED_BIT = 17,
		STRUNG_BIT = 18, SINK = 19;
	/** The painted stages' bits together (char, dark, bare, grey, frozen): what changes a body's skin. */
	public static final int PAINT_MASK = (1 << 13) - 1;
	/** Steps a fading body goes through before it is gone (a part at a time), and a full sink (percent). */
	public static final int FADE_STEPS = 6, SINK_PERCENT = 100;
	/** The most a body sinks into the ground (blocks) when its sink is full. */
	public static final float SINK_BLOCKS = 0.3F;

	private Wear() {
	}

	private static int mask(int shift) {
		return shift == SINK ? 0x7F : 0x7;
	}

	public static int get(int wear, int shift) {
		return wear >> shift & mask(shift);
	}

	public static int with(int wear, int shift, int value) {
		return wear & ~(mask(shift) << shift) | (value & mask(shift)) << shift;
	}

	public static boolean flag(int wear, int bit) {
		return (wear >> bit & 1) != 0;
	}

	public static int withFlag(int wear, int bit, boolean on) {
		return on ? wear | 1 << bit : wear & ~(1 << bit);
	}

	public static boolean frozen(int wear) {
		return flag(wear, FROZEN_BIT);
	}

	public static boolean fading(int wear) {
		return flag(wear, FADING_BIT);
	}

	public static boolean crushed(int wear) {
		return flag(wear, CRUSHED_BIT);
	}

	/** How far down it has sunk, 0 to 1. */
	public static float sunk(int wear) {
		return get(wear, SINK) / (float) SINK_PERCENT;
	}

	/**
	 * The parts a fading body has lost by now (as {@code CorpseEntity#PART_} bits, the way a body's missing parts are): its
	 * legs first, then its arms, then its head.
	 */
	public static int hidden(int wear) {
		if (!fading(wear)) {
			return 0;
		}
		int step = get(wear, FADE);
		int parts = 0;
		int[] order = {CorpseEntity.PART_RIGHT_LEG, CorpseEntity.PART_LEFT_LEG, CorpseEntity.PART_RIGHT_ARM, CorpseEntity.PART_LEFT_ARM, CorpseEntity.PART_HEAD};
		for (int i = 0; i < order.length && i < step; i++) {
			parts |= order[i];
		}
		return parts;
	}
}
