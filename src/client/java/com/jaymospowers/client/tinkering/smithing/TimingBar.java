package com.jaymospowers.client.tinkering.smithing;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The timing bar of the Smithing Anvil, shared with the lapidary wheel: a dark back, the good zone, the perfect zone
 * and the arrow sweeping across. (0.19.87) Taken out of {@code SmithingClient.bar}; the anvil looks the same as before.
 */
public final class TimingBar {
	public static final int BACK = 0xA0000000, GOOD_COLOR = 0xFFE0A030, PERFECT_COLOR = 0xFF60E060, ARROW_COLOR = 0xFFFFFFFF;

	private TimingBar() {
	}

	/**
	 * Draws the bar with its left end at {@code left}, {@code halfWidth * 2} wide. The zones are {@code good} and
	 * {@code perfect} bar units either side of {@code centre}; {@code arrow} is 0 to 100; {@code border} is the frame's colour.
	 */
	public static void draw(GuiGraphicsExtractor graphics, int left, int y, int halfWidth, int height, float centre, float good, float perfect,
		float arrow, int border) {
		int width = halfWidth * 2;
		graphics.fill(left - 2, y - 2, left + width + 2, y + height + 2, border);
		graphics.fill(left - 1, y - 1, left + width + 1, y + height + 1, BACK);
		graphics.fill(px(left, width, centre - good), y, px(left, width, centre + good), y + height, GOOD_COLOR);
		graphics.fill(px(left, width, centre - perfect), y, px(left, width, centre + perfect), y + height, PERFECT_COLOR);
		int notch = px(left, width, arrow);
		graphics.fill(notch - 1, y - 3, notch + 1, y + height + 3, ARROW_COLOR);
	}

	/** Where a bar position (0 to 100) falls on the screen. */
	public static int px(int left, int width, float at) {
		return left + Math.round(width * Mth.clamp(at, 0.0F, 100.0F) / 100.0F);
	}
}
