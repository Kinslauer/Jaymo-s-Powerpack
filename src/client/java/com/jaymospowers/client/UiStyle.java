package com.jaymospowers.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.0) The look the H screen ({@link PowerScreen}) and the Z Fighter training screen have, for the other Powers
 * screens to share (the same one Jaymo's Kingdoms' {@code Ui} draws): a dark see-through panel with a coloured stripe
 * along its top, a header (title in the accent, a dim line under it, a rule), striped rows that light up under the
 * mouse, and a box at the bottom that shows the hovered row in full.
 */
public final class UiStyle {
	public static final int PANEL = 0xD0101018, ROW = 0x30FFFFFF, ROW_ALT = 0x18FFFFFF, ROW_HOVER = 0x50FFFFFF, ROW_PICKED = 0x40FFFFFF,
		CHIP = 0xA0000000, TEXT = 0xFFFFFFFF, DIM = 0xFFA8A8B8, HINT = 0xFF707080, BAR_BACK = 0xFF303030;
	/** Height of the header (title, dim line, rule), and of one wrapped line in a detail box. */
	public static final int HEADER = 34, LINE = 9;

	private UiStyle() {
	}

	/** The panel and its stripe. */
	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int accent) {
		graphics.fill(x, y, x + w, y + h, PANEL);
		graphics.fill(x, y, x + w, y + 2, accent);
	}

	/**
	 * The header: {@code icon} (if any) top-left, {@code title} beside it in the accent, {@code sub} dim under it, both
	 * cut to {@code textW} (0: the panel's width), and a rule across under them.
	 */
	public static void header(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, @Nullable ItemStack icon, Component title,
		@Nullable Component sub, int accent, int textW) {
		int tx = x + 8;
		if (icon != null && !icon.isEmpty()) {
			graphics.item(icon, x + 8, y + 8);
			tx = x + 28;
		}
		int room = textW > 0 ? textW : x + w - 8 - tx;
		line(graphics, font, title, tx, y + 7, room, accent);
		if (sub != null) {
			line(graphics, font, sub, tx, y + 18, room, DIM);
		}
		rule(graphics, x + 6, x + w - 6, y + HEADER - 4);
	}

	/** A thin rule across, between a header and the rows or the rows and the detail box. */
	public static void rule(GuiGraphicsExtractor graphics, int x0, int x1, int y) {
		graphics.fill(x0, y, x1, y + 1, ROW);
	}

	/** One striped row: lighter every other one, lighter still under the mouse. */
	public static void row(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int index, boolean hover) {
		graphics.fill(x0, y0, x1, y1, hover ? ROW_HOVER : index % 2 == 0 ? ROW : ROW_ALT);
	}

	/** A thin bar, {@code share} of it filled. */
	public static void bar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float share, int color) {
		graphics.fill(x, y, x + w, y + h, BAR_BACK);
		graphics.fill(x, y, x + Math.round(w * Math.max(0.0F, Math.min(1.0F, share))), y + h, color);
	}

	/** (UI text pass) How a line too long for its room slides (a pause each end, ms per pixel), and how a detail box too full scrolls (ms per line). */
	public static final long SLIDE_PAUSE_MS = 1500L, SLIDE_MS_PER_PX = 35L, SCROLL_PAUSE_MS = 3000L, SCROLL_MS_PER_LINE = 1100L;

	/** One line of text. If it would run past {@code width} it slides slowly back and forth inside it, so all of it shows. */
	public static void line(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int color) {
		String s = text.getString();
		int over = font.width(s) - width;
		if (over <= 0) {
			graphics.text(font, s, x, y, color);
			return;
		}
		graphics.enableScissor(x, y - 1, x + Math.max(0, width), y + LINE + 1);
		graphics.text(font, s, x - slide(over, SLIDE_MS_PER_PX, SLIDE_PAUSE_MS), y, color);
		graphics.disableScissor();
	}

	/** The same, right-aligned to {@code right} (sliding inside {@code width} left of it when too long). */
	public static void lineRight(GuiGraphicsExtractor graphics, Font font, Component text, int right, int y, int width, int color) {
		int textW = font.width(text.getString());
		if (textW <= width) {
			graphics.text(font, text.getString(), right - textW, y, color);
		} else {
			line(graphics, font, text, right - width, y, width, color);
		}
	}

	/** The same, centred on {@code centre} (sliding inside {@code width} round it when too long). */
	public static void lineCentred(GuiGraphicsExtractor graphics, Font font, Component text, int centre, int y, int width, int color) {
		int textW = font.width(text.getString());
		if (textW <= width) {
			graphics.text(font, text.getString(), centre - textW / 2, y, color);
		} else {
			line(graphics, font, text, centre - width / 2, y, width, color);
		}
	}

	/** Where a slide of {@code over} steps is now (0 to {@code over}): a pause, along at {@code msPerStep}, a pause, and back. */
	public static int slide(int over, long msPerStep, long pause) {
		long run = over * msPerStep, t = Util.getMillis() % (2 * (pause + run));
		if (t < pause) {
			return 0;
		}
		t -= pause;
		if (t < run) {
			return (int)(t / msPerStep);
		}
		t -= run;
		return t < pause ? over : over - (int)Math.min(over, (t - pause) / msPerStep);
	}

	/** {@code s} cut to {@code width} with "..." (for HUD bits with no room to slide; screens use {@link #line}). */
	public static String cut(Font font, String s, int width) {
		if (font.width(s) <= width) {
			return s;
		}
		return font.plainSubstrByWidth(s, Math.max(0, width - font.width("..."))) + "...";
	}

	/**
	 * The detail box: a rule, then {@code name} in {@code color} (if any) and {@code text} wrapped under it, in
	 * {@code lines} lines. With no name, the text is dim (a hint, or the screen's lead). Text that needs more lines
	 * scrolls slowly up through the box and back (a thin bar at the side shows where), so none of it is cut off.
	 */
	public static void detail(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int lines, @Nullable Component name, int color,
		List<Component> text) {
		rule(graphics, x + 6, x + w - 6, y);
		int ty = y + 5, room = lines;
		boolean named = name != null && !name.getString().isEmpty();
		if (named) {
			line(graphics, font, name, x + 9, ty, w - 18, color);
			ty += 10;
			room--;
		}
		if (room <= 0) {
			return;
		}
		List<FormattedCharSequence> rows = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		for (Component part : text) {
			int partColor = part.getStyle().getColor() != null ? 0xFF000000 | part.getStyle().getColor().getValue() : named ? TEXT : DIM;
			for (FormattedCharSequence seq : font.split(part, w - 18)) {
				rows.add(seq);
				colors.add(partColor);
			}
		}
		int view = room * LINE, over = Math.max(0, rows.size() - room) * LINE;
		int shift = over > 0 ? slide(over, SCROLL_MS_PER_LINE / LINE, SCROLL_PAUSE_MS) : 0;
		if (over > 0) {
			graphics.enableScissor(x, ty - 1, x + w, ty + view);
		}
		for (int i = 0; i < rows.size(); i++) {
			int ly = ty + i * LINE - shift;
			if (ly > ty - LINE && ly < ty + view) {
				graphics.text(font, rows.get(i), x + 9, ly, colors.get(i));
			}
		}
		if (over > 0) {
			graphics.disableScissor();
			scrollbar(graphics, x + w - 6, ty, view - 1, view, view + over, shift);
		}
	}

	/** How many lines {@link #detail} needs for these, at this width. */
	public static int detailLines(Font font, int w, @Nullable Component name, List<Component> text) {
		int n = name != null && !name.getString().isEmpty() ? 1 : 0;
		for (Component part : text) {
			n += font.split(part, w - 18).size();
		}
		return n;
	}

	/** The height a detail box of {@code lines} lines takes. */
	public static int detailHeight(int lines) {
		return 5 + lines * LINE + 4;
	}

	/**
	 * {@code text} wrapped to {@code width} in {@code lines} lines {@code lineH} apart; more than fits scrolls slowly up
	 * through them and back (a thin bar just right of {@code width} shows where), so none is cut off. Returns the lines used.
	 */
	public static int wrapped(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int lines, int lineH, int color,
		boolean shadow) {
		List<FormattedCharSequence> split = font.split(text, width);
		int n = Math.min(lines, split.size());
		if (n <= 0) {
			return 0;
		}
		int view = n * lineH, over = (split.size() - n) * lineH;
		int shift = over > 0 ? slide(over, SCROLL_MS_PER_LINE / lineH, SCROLL_PAUSE_MS) : 0;
		if (over > 0) {
			graphics.enableScissor(x - 1, y - 1, x + width + 4, y + view);
		}
		for (int i = 0; i < split.size(); i++) {
			int ly = y + i * lineH - shift;
			if (ly > y - lineH && ly < y + view) {
				graphics.text(font, split.get(i), x, ly, color, shadow);
			}
		}
		if (over > 0) {
			graphics.disableScissor();
			scrollbar(graphics, x + width + 2, y, view - 1, view, view + over, shift);
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
