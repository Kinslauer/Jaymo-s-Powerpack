package com.jaymospowers.client.tinkering;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * (0.10.120) The Tinkering stations drawn like the H screen and the Z Fighter training screen, in place of Tinkers'
 * grey: a dark see-through panel with a bronze stripe along its top, sunken wells for the slots, light labels.
 */
final class TinkerUi {
	static final int PANEL = 0xD0101018, ACCENT = 0xFFD0843A, TEXT = 0xFFFFFFFF, DIM = 0xFFA8A8B8, ROW = 0x30FFFFFF, ROW_HOVER = 0x50FFFFFF,
		WELL_EDGE = 0xFF34343E, WELL = 0xFF0C0C12;

	private TinkerUi() {
	}

	static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, PANEL);
		graphics.fill(x, y, x + w, y + 2, ACCENT);
	}

	/** A sunken box an item, a slot or a pool sits in. */
	static void well(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
		graphics.fill(x, y, x + w, y + h, WELL_EDGE);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, WELL);
	}

	/** A well behind every slot that's showing; the slot numbered {@code big} (an output) gets a larger one. */
	static void slots(GuiGraphicsExtractor graphics, AbstractContainerMenu menu, int left, int top, int big) {
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
	static void arrowRight(GuiGraphicsExtractor graphics, int x, int y, int length, int color) {
		graphics.fill(x, y - 1, x + length - 4, y + 1, color);
		for (int i = 0; i < 5; i++) {
			graphics.fill(x + length - 5 + i, y - 4 + i, x + length - 4 + i, y + 4 - i, color);
		}
	}

	/** The same pointing down, from ({@code x}, {@code y}) on the shaft's middle. */
	static void arrowDown(GuiGraphicsExtractor graphics, int x, int y, int length, int color) {
		graphics.fill(x - 1, y, x + 1, y + length - 4, color);
		for (int i = 0; i < 5; i++) {
			graphics.fill(x - 4 + i, y + length - 5 + i, x + 4 - i, y + length - 4 + i, color);
		}
	}

	/** The screen's title in the accent and the inventory's in dim, where vanilla puts them. */
	static void labels(GuiGraphicsExtractor graphics, Font font, Component title, int titleX, int titleY, Component inventory, int invX, int invY) {
		graphics.text(font, title, titleX, titleY, ACCENT, false);
		graphics.text(font, inventory, invX, invY, DIM, false);
	}
}
