package com.jaymospowers.client.tinkering;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** The info panel beside a Tinkers table's screen: (0.10.120) drawn like the H screen, a dark box with a bronze stripe and wrapped lines. */
final class SidePanel {
	static final int WIDTH = 118, PAD = 5;
	private static final int TEXT = TinkerUi.TEXT;

	private SidePanel() {
	}

	static void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, Component title, List<Component> lines) {
		int width = WIDTH - 2 * PAD;
		List<FormattedCharSequence> titleLines = font.split(title, width);
		int height = PAD * 2 + 1 + titleLines.size() * 10 + 3;
		for (Component line : lines) {
			height += font.split(line, width).size() * 10;
		}
		TinkerUi.panel(graphics, x, y, WIDTH, height);
		int ty = y + PAD + 1;
		for (FormattedCharSequence line : titleLines) {
			graphics.text(font, line, x + PAD, ty, TinkerUi.ACCENT, true);
			ty += 10;
		}
		graphics.fill(x + PAD - 1, ty, x + WIDTH - PAD + 1, ty + 1, TinkerUi.ROW);
		ty += 3;
		for (Component line : lines) {
			for (FormattedCharSequence part : font.split(line, width)) {
				graphics.text(font, part, x + PAD, ty, TEXT, true);
				ty += 10;
			}
		}
	}
}
