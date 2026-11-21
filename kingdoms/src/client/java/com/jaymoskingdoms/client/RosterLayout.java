package com.jaymoskingdoms.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The one shape of a roster page (the Party and the Followers' orders): the gold frame, a title strip, a list down the
 * left (a row each, the picked one lit) and a detail panel on the right that shows the row you point at, or else the one
 * you picked.
 */
final class RosterLayout {
	/** The list's width, the gap between the list and the detail, the title strip's height, a list row's height. */
	static final int LIST_W = 126, GAP = 6, HEAD = 24, ROW = 24, PAD = 8;
	/** The picked row's tint. */
	static final int PICKED = 0x40FFD54A;

	private RosterLayout() {
	}

	/** The frame and the title (an icon-less strip), then the two panels' wells. */
	static void frame(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h, Component title, Component subtitle) {
		Ui.panelFramed(graphics, x, y, w, h, Ui.GOLD);
		Ui.line(graphics, font, title, x + 14, y + 8, w - 28, Ui.GOLD);
		if (subtitle != null) {
			Ui.lineRight(graphics, font, subtitle, x + w - 14, y + 8, w / 2 - 20, Ui.DIM);
		}
		Ui.rule(graphics, x + PAD, x + w - PAD, y + HEAD);
	}

	static int listX(int x) {
		return x + PAD;
	}

	static int detailX(int x) {
		return x + PAD + LIST_W + GAP;
	}

	static int detailW(int w) {
		return w - 2 * PAD - LIST_W - GAP;
	}

	static int bodyY(int y) {
		return y + HEAD + 6;
	}

	/** Backs the list and the detail panel, {@code h} tall from the body's top. */
	static void wells(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		Ui.recess(graphics, listX(x), bodyY(y), LIST_W, h);
		Ui.recess(graphics, detailX(x), bodyY(y), detailW(w), h);
	}

	/** A list row: lit when {@code picked}, brighter under the mouse. */
	static void row(GuiGraphicsExtractor graphics, int x, int y, int index, boolean picked, boolean hover) {
		Ui.row(graphics, x + 2, y, x + LIST_W - 2, y + ROW - 2, index, hover);
		if (picked) {
			graphics.fill(x + 2, y, x + LIST_W - 2, y + ROW - 2, PICKED);
		}
	}
}
