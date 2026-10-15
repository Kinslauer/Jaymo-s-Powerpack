package com.jaymoskitchen.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * (0.4.40) The look every Kitchen screen shares, the one Jaymo's Powers' H screen and Jaymo's Kingdoms have: a dark
 * see-through panel with a warm stripe along its top, a header (icon, title, a dim line under it, a rule), striped
 * rows that light up under the mouse, sunken wells for items and slots, and a box at the bottom that tells about the
 * hovered thing in full.
 */
public final class KitchenUi {
	public static final int PANEL = 0xD0101018, ROW = 0x30FFFFFF, ROW_ALT = 0x18FFFFFF, ROW_HOVER = 0x50FFFFFF,
		TEXT = 0xFFFFFFFF, DIM = 0xFFA8A8B8, HINT = 0xFF707080, BAR_BACK = 0xFF303030, GOOD = 0xFF66C266, OK = 0xFFFFD54A,
		BAD = 0xFFE05050, WELL_EDGE = 0xFF34343E, WELL = 0xFF0C0C12;
	/** The Kitchen's accent: a warm, cooked-crust orange. */
	public static final int ACCENT = 0xFFE0A050;
	/** Where the rows start under a {@link #header}. */
	public static final int HEADER = 34;
	/** Height of one wrapped line in a detail box. */
	public static final int LINE = 9;

	private KitchenUi() {
	}

	/** The panel and its stripe. */
	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int accent) {
		panel(graphics, x, y, w, h, accent, PANEL);
	}

	/** The same, with a lighter (more see-through) fill, for views where the fire behind should show. */
	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int accent, int fill) {
		graphics.fill(x, y, x + w, y + h, fill);
		graphics.fill(x, y, x + w, y + 2, accent);
	}

	/**
	 * The header: {@code icon} (if any) at the top left, {@code title} in the accent beside it, {@code sub} dim under
	 * it, and a rule across at {@code y + HEADER - 4}. Both lines end in "..." if they'd run past {@code right}.
	 */
	public static void header(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, ItemStack icon, Component title,
		Component sub, int accent, int right) {
		int tx = icon == null ? x + 9 : x + 28;
		if (icon != null) {
			graphics.item(icon, x + 8, y + 8);
		}
		int width = right - tx;
		line(graphics, font, title, tx, y + 8, width, accent);
		if (sub != null) {
			line(graphics, font, sub, tx, y + 19, width, DIM);
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

	/** A sunken box an item or a slot sits in. */
	public static void well(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, WELL_EDGE);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, WELL);
	}

	/** An item in an 18x18 well whose inside starts at (x, y), with its count. */
	public static void itemWell(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
		well(graphics, x - 1, y - 1, 18, 18);
		graphics.item(stack, x, y);
		graphics.itemDecorations(font, stack, x, y, null);
	}

	/** A bigger 26x26 well for something made (an output, a finished dish), the item centred in it. */
	public static void outputWell(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
		well(graphics, x - 5, y - 5, 26, 26);
		graphics.item(stack, x, y);
		graphics.itemDecorations(font, stack, x, y, null);
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

	/** The same, centred on {@code cx}. */
	public static void lineCentered(GuiGraphicsExtractor graphics, Font font, Component text, int cx, int y, int width, int color) {
		String s = cut(font, text.getString(), width);
		graphics.text(font, s, cx - font.width(s) / 2, y, color);
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
	public static void detail(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int lines, Component name, int color,
		List<Component> text) {
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
