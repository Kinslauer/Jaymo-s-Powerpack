package com.jaymospowers.client.tinkering;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** The info panel beside a Tinkers table's screen, like Tinkers' own: a dark box with wrapped lines. */
final class SidePanel {
	static final int WIDTH = 118, PAD = 5;
	private static final int BACK = 0xE0101010, EDGE = 0xFF5A5A5A, TEXT = 0xFFFFFFFF;

	private SidePanel() {
	}

	static void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, Component title, List<Component> lines) {
		int width = WIDTH - 2 * PAD;
		List<FormattedCharSequence> titleLines = font.split(title, width);
		int height = PAD * 2 + titleLines.size() * 10 + 3;
		for (Component line : lines) {
			height += font.split(line, width).size() * 10;
		}
		graphics.fill(x, y, x + WIDTH, y + height, BACK);
		graphics.fill(x, y, x + WIDTH, y + 1, EDGE);
		graphics.fill(x, y + height - 1, x + WIDTH, y + height, EDGE);
		graphics.fill(x, y, x + 1, y + height, EDGE);
		graphics.fill(x + WIDTH - 1, y, x + WIDTH, y + height, EDGE);
		int ty = y + PAD;
		for (FormattedCharSequence line : titleLines) {
			graphics.text(font, line, x + PAD, ty, 0xFFFFD866, true);
			ty += 10;
		}
		ty += 3;
		for (Component line : lines) {
			for (FormattedCharSequence part : font.split(line, width)) {
				graphics.text(font, part, x + PAD, ty, TEXT, true);
				ty += 10;
			}
		}
	}
}
