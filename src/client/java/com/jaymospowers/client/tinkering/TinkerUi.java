package com.jaymospowers.client.tinkering;

import com.jaymospowers.client.UiStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * (0.10.127) The Tinkering stations drawn like the H screen and the Z Fighter training screen, in place of Tinkers'
 * grey: a dark see-through panel with a bronze stripe along its top, sunken wells for the slots, light labels.
 */
public final class TinkerUi {
	/** The stations' bronze stripe; everything else is the shared screen style's ({@link UiStyle}). */
	public static final int ACCENT = 0xFFD0843A;
	public static final int PANEL = UiStyle.PANEL, TEXT = UiStyle.TEXT, DIM = UiStyle.DIM, ROW = UiStyle.ROW, ROW_HOVER = UiStyle.ROW_HOVER,
		WELL_EDGE = UiStyle.WELL_EDGE, WELL = UiStyle.WELL;

	private TinkerUi() {
	}

	public static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		UiStyle.panel(graphics, x, y, w, h, ACCENT);
	}

	/** A sunken box an item, a slot or a pool sits in. */
	public static void well(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		UiStyle.well(graphics, x, y, w, h);
	}

	/** A well behind every slot that's showing; the slot numbered {@code big} (an output) gets a larger one. */
	public static void slots(GuiGraphicsExtractor graphics, AbstractContainerMenu menu, int left, int top, int big) {
		for (int i = 0; i < menu.slots.size(); i++) {
			Slot slot = menu.slots.get(i);
			if (!slot.isActive()) {
				continue;
			}
			if (i == big) {
				well(graphics, left + slot.x - 5, top + slot.y - 5, 26, 26);
				graphics.fill(left + slot.x - 5, top + slot.y + 20, left + slot.x + 21, top + slot.y + 21, ACCENT);
			} else {
				well(graphics, left + slot.x - 1, top + slot.y - 1, 18, 18);
			}
		}
	}

	/** An arrow pointing right, from ({@code x}, {@code y}) on the shaft's middle, {@code length} long. */
	public static void arrowRight(GuiGraphicsExtractor graphics, int x, int y, int length, int color) {
		graphics.fill(x, y - 1, x + length - 4, y + 1, color);
		for (int i = 0; i < 5; i++) {
			graphics.fill(x + length - 5 + i, y - 4 + i, x + length - 4 + i, y + 4 - i, color);
		}
	}

	/** The same pointing down, from ({@code x}, {@code y}) on the shaft's middle. */
	public static void arrowDown(GuiGraphicsExtractor graphics, int x, int y, int length, int color) {
		graphics.fill(x - 1, y, x + 1, y + length - 4, color);
		for (int i = 0; i < 5; i++) {
			graphics.fill(x - 4 + i, y + length - 5 + i, x + 4 - i, y + length - 4 + i, color);
		}
	}

	/** The screen's title in the accent and the inventory's in dim, where vanilla puts them. */
	public static void labels(GuiGraphicsExtractor graphics, Font font, Component title, int titleX, int titleY, Component inventory, int invX, int invY) {
		graphics.text(font, title, titleX, titleY, ACCENT, false);
		graphics.text(font, inventory, invX, invY, DIM, false);
	}
}
