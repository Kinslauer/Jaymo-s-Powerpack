package com.jaymoskingdoms.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;

/**
 * (0.7.40) The look every Kingdoms screen shares, the one Jaymo's Powers' screens have (the H screen, the Z Fighter
 * training screen): a dark see-through panel with a coloured stripe along its top, a header, striped rows that light
 * up under the mouse, and a box at the bottom that shows the hovered row in full.
 */
public final class Ui {
	public static final int PANEL = 0xD0101018, ROW = 0x30FFFFFF, ROW_ALT = 0x18FFFFFF, ROW_HOVER = 0x50FFFFFF, ROW_PICKED = 0x40FFFFFF,
		CHIP = 0xA0000000, TEXT = 0xFFFFFFFF, DIM = 0xFFA8A8B8, HINT = 0xFF707080, BAR_BACK = 0xFF303030, GOOD = 0xFF66C266,
		BAD = 0xFFE05050, WELL_EDGE = 0xFF34343E, WELL = 0xFF0C0C12;
	/** The accents: the Kingdoms gold (journal, class pick, stables) and one per skill pillar. */
	public static final int GOLD = 0xFFFFC21A, SURVIVAL = 0xFF66C266, STEALTH = 0xFF9A7BE0, STRENGTH = 0xFFE06A4A;
	/** Height of one wrapped line in a detail box. */
	public static final int LINE = 9;

	private Ui() {
	}

	/** The panel and its stripe. */
	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int accent) {
		graphics.fill(x, y, x + w, y + h, PANEL);
		graphics.fill(x, y, x + w, y + 2, accent);
	}

	/** A thin rule across, between a header and the rows or the rows and the detail box. */
	public static void rule(GuiGraphicsExtractor graphics, int x0, int x1, int y) {
		graphics.fill(x0, y, x1, y + 1, ROW);
	}

	/** One striped row: lighter every other one, lighter still under the mouse. */
	public static void row(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int index, boolean hover) {
		graphics.fill(x0, y0, x1, y1, hover ? ROW_HOVER : index % 2 == 0 ? ROW : ROW_ALT);
	}

	/** A sunken box an item or a slot sits in. */
	public static void well(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, WELL_EDGE);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, WELL);
	}

	/** A well behind every slot of a container screen that's showing. */
	public static void slots(GuiGraphicsExtractor graphics, List<Slot> slots, int left, int top) {
		for (Slot slot : slots) {
			if (slot.isActive()) {
				well(graphics, left + slot.x - 1, top + slot.y - 1, 18, 18);
			}
		}
	}

	/** A thin bar, {@code share} of it filled. */
	public static void bar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float share, int color) {
		graphics.fill(x, y, x + w, y + h, BAR_BACK);
		graphics.fill(x, y, x + Math.round(w * Math.max(0.0F, Math.min(1.0F, share))), y + h, color);
	}

	/** One line of text, cut with "..." if it would run past {@code width}. */
	public static void line(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int color) {
		graphics.text(font, cut(font, text.getString(), width), x, y, color);
	}

	/** The same, right-aligned to {@code right}. */
	public static void lineRight(GuiGraphicsExtractor graphics, Font font, Component text, int right, int y, int width, int color) {
		String s = cut(font, text.getString(), width);
		graphics.text(font, s, right - font.width(s), y, color);
	}

	public static String cut(Font font, String s, int width) {
		if (font.width(s) <= width) {
			return s;
		}
		return font.plainSubstrByWidth(s, Math.max(0, width - font.width("..."))) + "...";
	}

	/**
	 * The detail box: a rule, then {@code name} in {@code color} (if any) and {@code text} wrapped under it, in at most
	 * {@code lines} lines; "..." when it runs over. With no name, the text is dim (a hint, or the screen's lead).
	 */
	public static void detail(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int lines, Component name, int color, List<Component> text) {
		rule(graphics, x + 6, x + w - 6, y);
		int ty = y + 5, room = lines;
		boolean named = name != null && !name.getString().isEmpty();
		if (named) {
			line(graphics, font, name, x + 9, ty, w - 18, color);
			ty += 10;
			room--;
		}
		int drawn = 0;
		outer:
		for (Component part : text) {
			int partColor = part.getStyle().getColor() != null ? 0xFF000000 | part.getStyle().getColor().getValue() : named ? TEXT : DIM;
			List<FormattedCharSequence> split = font.split(part, w - 18);
			for (FormattedCharSequence seq : split) {
				if (drawn >= room) {
					break outer;
				}
				boolean last = drawn == room - 1 && (seq != split.getLast() || part != text.getLast());
				graphics.text(font, seq, x + 9, ty + drawn * LINE, partColor);
				if (last) {
					graphics.text(font, "...", x + 9 + font.width(seq), ty + drawn * LINE, DIM);
				}
				drawn++;
			}
		}
	}

	/** How many lines {@link #detail} needs for these, at this width. */
	public static int detailLines(Font font, int w, Component name, List<Component> text) {
		int n = name != null && !name.getString().isEmpty() ? 1 : 0;
		for (Component part : text) {
			n += font.split(part, w - 18).size();
		}
		return n;
	}

	/** A thin scrollbar at {@code x}, from {@code top} down {@code track}. */
	public static void scrollbar(GuiGraphicsExtractor graphics, int x, int top, int track, int view, int content, int scroll) {
		if (content <= view) {
			return;
		}
		int thumb = Math.max(8, track * view / content);
		int at = top + (track - thumb) * scroll / Math.max(1, content - view);
		graphics.fill(x, top, x + 2, top + track, 0x40FFFFFF);
		graphics.fill(x, at, x + 2, at + thumb, 0xC0FFFFFF);
	}

	public static boolean over(int mouseX, int mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}
}
