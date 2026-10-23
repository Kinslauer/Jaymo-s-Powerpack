package com.jaymospowers.client.tinkering;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * The info panel beside a Tinkers table's screen: (0.10.127) drawn like the H screen, a dark box with a bronze stripe and
 * wrapped lines. (0.15.135) It stops at the bottom of the window and scrolls with the wheel when its lines don't fit, and
 * remembers where it was drawn so JEI keeps its item list clear of it ({@link #area()}).
 */
final class SidePanel {
	static final int WIDTH = 118, PAD = 5;
	/** Kept clear between the panel and the bottom of the window. */
	static final int MARGIN = 4;
	/** Rows moved per wheel notch. */
	static final int SCROLL_ROWS = 3;
	private static final int TEXT = TinkerUi.TEXT;
	private static final int ROW_H = 10;

	private int x, y, height, scroll, maxScroll;
	private String lastTitle = "";

	/** Draws the panel at (x, y), no lower than {@code bottom}. */
	void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int bottom, Component title, List<Component> lines) {
		int width = WIDTH - 2 * PAD;
		List<FormattedCharSequence> titleLines = font.split(title, width);
		List<FormattedCharSequence> rows = new ArrayList<>();
		for (Component line : lines) {
			rows.addAll(font.split(line, width));
		}
		// a different panel (recipes, then a result's stats) starts at its top
		if (!title.getString().equals(lastTitle)) {
			lastTitle = title.getString();
			scroll = 0;
		}
		int head = PAD + 1 + titleLines.size() * ROW_H + 3;
		int visible = Math.max(1, (bottom - MARGIN - y - head - PAD) / ROW_H);
		visible = Math.min(visible, rows.size());
		maxScroll = Math.max(0, rows.size() - visible);
		scroll = Math.clamp(scroll, 0, maxScroll);
		this.x = x;
		this.y = y;
		this.height = head + visible * ROW_H + PAD;
		TinkerUi.panel(graphics, x, y, WIDTH, height);
		int ty = y + PAD + 1;
		for (FormattedCharSequence line : titleLines) {
			graphics.text(font, line, x + PAD, ty, TinkerUi.ACCENT, true);
			ty += ROW_H;
		}
		graphics.fill(x + PAD - 1, ty, x + WIDTH - PAD + 1, ty + 1, TinkerUi.ROW);
		ty += 3;
		for (int i = scroll; i < scroll + visible; i++) {
			graphics.text(font, rows.get(i), x + PAD, ty, TEXT, true);
			ty += ROW_H;
		}
		if (maxScroll > 0) {
			// a thin scrollbar in the right margin, clear of the text
			int trackTop = y + head, trackH = visible * ROW_H;
			int thumbH = Math.max(6, trackH * visible / rows.size());
			int thumbY = trackTop + (trackH - thumbH) * scroll / maxScroll;
			graphics.fill(x + WIDTH - 3, trackTop, x + WIDTH - 2, trackTop + trackH, TinkerUi.ROW);
			graphics.fill(x + WIDTH - 3, thumbY, x + WIDTH - 2, thumbY + thumbH, TinkerUi.ACCENT);
		}
	}

	/** Scrolls the lines when the mouse is over the panel; true if it took the wheel. */
	boolean scroll(double mouseX, double mouseY, double amount) {
		if (maxScroll == 0 || !contains(mouseX, mouseY)) {
			return false;
		}
		scroll = Math.clamp(scroll - (int) Math.signum(amount) * SCROLL_ROWS, 0, maxScroll);
		return true;
	}

	boolean contains(double mouseX, double mouseY) {
		return height > 0 && mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height;
	}

	/** Where the panel was last drawn, or null before it has been. */
	@Nullable Rect2i area() {
		return height > 0 ? new Rect2i(x, y, WIDTH, height) : null;
	}
}
