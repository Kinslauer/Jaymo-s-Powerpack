package com.jaymospowers.jewellery;

/**
 * (0.19.87) The numbers of the lapidary wheel's timing game. The bar and its zones are the Smithing Anvil's
 * ({@code Forging.perfectHalf/goodHalf/speed/judge}); what is new is the wheel's spin-up between strokes. Tune here.
 */
public final class GemCutting {
	/** Ticks the wheel needs after a stroke before the next one, or the stroke is a rush that chips the gem. */
	public static final int SETTLE_TICKS = 16;
	/** Ticks of the settle the server forgives (lag only makes strokes later, but clocks differ by a tick or two). */
	public static final int SETTLE_GRACE = 2;
	/** Strokes closer together than this are dropped as packet spam (they are not chips). */
	public static final int MIN_STROKE_GAP = 3;
	/** How often the wheel checks its cutter has not wandered off (ticks), how far is too far (blocks, squared). */
	public static final int CHECK_TICKS = 20;
	public static final double LEAVE_SQ = 10 * 10;
	/** A cutter who hasn't struck in this long gets the game sent again on their next use. */
	public static final int RESEND_TICKS = 40;
	/** The result code of a chipped stroke (besides Forging.MISS 0, GOOD 1, PERFECT 2). */
	public static final int CHIP = 3;

	private GemCutting() {
	}
}
