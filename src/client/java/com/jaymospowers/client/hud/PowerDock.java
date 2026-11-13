package com.jaymospowers.client.hud;

/**
 * (HUD rework) The active power's block: the right dock's bottom row ({@link HudZones#DOCK_RIGHT}). {@code PowerHud} places it
 * each frame ({@link #begin}) before the power draws; the power puts its chip row on the block's bottom edge with
 * {@link #origin} and its lines (flight readout, ki, form timer...) over the chips, inside the block. Every power used to work
 * this out itself (right of the hotbar, or over the hunger bar on a narrow screen); the zone does that now, and keeps the
 * mount bars, the Rig panel and the rest from landing on the chips.
 */
public final class PowerDock {
	/** A block's usual height: the chip row and two lines over it. */
	public static final int DEFAULT_HEIGHT = HudStyle.CHIP + 2 + 26;
	/** The power block's row in the right dock (lowest: on the hotbar's level). */
	public static final int ROW = 10;
	private static int left, bottom;

	private PowerDock() {
	}

	/** Places this frame's block, {@code height} tall; false while a film hides the HUD. */
	public static boolean begin(int height) {
		int[] at = HudZones.place(HudZones.DOCK_RIGHT, "jaymospowers:power", ROW, 0, height);
		if (at == null) {
			return false;
		}
		left = at[0];
		bottom = at[1] + height;
		return true;
	}

	/** Places a second block in the row over the power's (a swallowed power's), {@code height} tall; false while hidden. */
	public static boolean beginAbove(String id, int height) {
		int[] at = HudZones.place(HudZones.DOCK_RIGHT, id, ROW + 1, 0, height);
		if (at == null) {
			return false;
		}
		left = at[0];
		bottom = at[1] + height;
		return true;
	}

	/** Top-left of a chip row {@code rowHeight} tall (a chip: {@link HudStyle#CHIP}), on the block's bottom edge. */
	public static int[] origin(int rowHeight) {
		return new int[] {left, bottom - rowHeight};
	}

	/** The block's left edge and bottom edge. */
	public static int left() {
		return left;
	}

	public static int bottom() {
		return bottom;
	}
}
