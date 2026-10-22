package com.jaymospowers.client.tinkering;

import com.jaymospowers.tinkering.StationBlock;
import com.jaymospowers.tinkering.table.TableExtras;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * What every Tinkers table screen draws besides its own (Tinkers' signature UX): the side inventory's panel at the left
 * (its slots are the menu's, {@link TableExtras}), and tabs along the top for the tables touching this one.
 */
final class TableUi {
	private static final int TAB_X = 4, TAB_W = 28, TAB_H = 24, TAB_STEP = 30, TAB_OVERLAP = 2;
	private static final int TAB_IDLE = 0xB0080810;

	private TableUi() {
	}

	/** Where the info panel goes: its usual place at the left, or the right when the side inventory has the left. */
	static int infoX(TableExtras extras, int left, int width) {
		return extras.size() > 0 ? left + width + 2 : left - SidePanel.WIDTH - 2;
	}

	/** The side inventory's panel and the tabs; draw after the screen's own panel and before its slot wells. */
	static void draw(GuiGraphicsExtractor graphics, Font font, TableExtras extras, int left, int top) {
		if (extras.size() > 0) {
			int rows = (extras.size() + TableExtras.SIDE_COLUMNS - 1) / TableExtras.SIDE_COLUMNS;
			int x = left + TableExtras.SIDE_X - SidePanel.PAD - 1, y = top;
			TinkerUi.panel(graphics, x, y, SidePanel.WIDTH, TableExtras.SIDE_Y + rows * 18 + 4);
			graphics.text(font, extras.title(), x + SidePanel.PAD, y + 6, TinkerUi.ACCENT, true);
			graphics.fill(x + SidePanel.PAD - 1, y + 16, x + SidePanel.WIDTH - SidePanel.PAD + 1, y + 17, TinkerUi.ROW);
		}
		List<StationBlock.Kind> tabs = extras.tabs();
		for (int i = 0; i < tabs.size(); i++) {
			int x = left + TAB_X + i * TAB_STEP, y = top - TAB_H + TAB_OVERLAP;
			if (tabs.get(i) == extras.self()) {
				TinkerUi.panel(graphics, x, y, TAB_W, TAB_H);
			} else {
				graphics.fill(x, y + 3, x + TAB_W, y + TAB_H - 1, TAB_IDLE);
				graphics.fill(x, y + 3, x + TAB_W, y + 4, TinkerUi.DIM);
			}
			graphics.item(new ItemStack(tabs.get(i).block()), x + 6, y + 6);
		}
	}

	/** The tab under the mouse, or null. */
	private static StationBlock.Kind tabAt(TableExtras extras, int left, int top, double mouseX, double mouseY) {
		List<StationBlock.Kind> tabs = extras.tabs();
		for (int i = 0; i < tabs.size(); i++) {
			int x = left + TAB_X + i * TAB_STEP, y = top - TAB_H + TAB_OVERLAP;
			if (mouseX >= x && mouseX < x + TAB_W && mouseY >= y + 3 && mouseY < y + TAB_H) {
				return tabs.get(i);
			}
		}
		return null;
	}

	/** The tab's table name under the mouse. */
	static void tooltip(GuiGraphicsExtractor graphics, Font font, TableExtras extras, int left, int top, int mouseX, int mouseY) {
		StationBlock.Kind kind = tabAt(extras, left, top, mouseX, mouseY);
		if (kind != null) {
			graphics.setTooltipForNextFrame(font, kind.block().getName(), mouseX, mouseY);
		}
	}

	/** A click on another table's tab asks the server to open it; true if it was a tab. */
	static boolean click(Minecraft minecraft, int containerId, TableExtras extras, int left, int top, double mouseX, double mouseY) {
		StationBlock.Kind kind = tabAt(extras, left, top, mouseX, mouseY);
		if (kind == null) {
			return false;
		}
		if (kind != extras.self()) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			minecraft.gameMode.handleInventoryButtonClick(containerId, TableExtras.TAB_BUTTONS + kind.ordinal());
		}
		return true;
	}
}
