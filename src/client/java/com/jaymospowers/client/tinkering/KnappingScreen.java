package com.jaymospowers.client.tinkering;

import com.jaymospowers.ground.knapping.Knapping;
import com.jaymospowers.ground.knapping.KnappingMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The knapping grid: the rock's chips as a 3x3 of buttons (a click knocks one out; the server decides), the rock on the
 * left, the head it has become on the right. A panel of the shapes beside it; hover one to see where its chips lie.
 */
public class KnappingScreen extends AbstractContainerScreen<KnappingMenu> {
	private static final int GRID_X = 61, GRID_Y = 14, CELL = 18;
	/** The start-over button, under the rock slot. */
	private static final int RESET_X = 8, RESET_Y = 56, RESET_W = 44, RESET_H = 12;
	/** The shapes panel beside the screen. */
	private static final int TILE = 20, TILE_COLUMNS = 4, SIDE_GAP = 2, SIDE_PAD = 4, SIDE_TOP = 16;

	public KnappingScreen(KnappingMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 166);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	private List<Knapping.Shape> shapes() {
		return Knapping.available();
	}

	private int sideX() {
		return leftPos + imageWidth + SIDE_GAP;
	}

	private int sideWidth() {
		return TILE_COLUMNS * TILE + SIDE_PAD * 2;
	}

	private int sideHeight() {
		return SIDE_TOP + (shapes().size() + TILE_COLUMNS - 1) / TILE_COLUMNS * TILE + SIDE_PAD;
	}

	/** The shape tile under the mouse, or -1. */
	private int tileAt(double mouseX, double mouseY) {
		int x = (int) Math.floor((mouseX - sideX() - SIDE_PAD) / TILE), y = (int) Math.floor((mouseY - topPos - SIDE_TOP) / TILE);
		if (x < 0 || y < 0 || x >= TILE_COLUMNS) {
			return -1;
		}
		int index = y * TILE_COLUMNS + x;
		return index < shapes().size() ? index : -1;
	}

	private int cellAt(double mouseX, double mouseY) {
		int x = (int) Math.floor((mouseX - leftPos - GRID_X) / CELL), y = (int) Math.floor((mouseY - topPos - GRID_Y) / CELL);
		return x < 0 || y < 0 || x >= Knapping.SIDE || y >= Knapping.SIDE ? -1 : y * Knapping.SIDE + x;
	}

	private boolean overReset(double mouseX, double mouseY) {
		return mouseX >= leftPos + RESET_X && mouseY >= topPos + RESET_Y && mouseX < leftPos + RESET_X + RESET_W && mouseY < topPos + RESET_Y + RESET_H;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		TinkerUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
		TinkerUi.slots(graphics, menu, leftPos, topPos, KnappingMenu.RESULT);
		TinkerUi.arrowRight(graphics, leftPos + 117, topPos + 43, 18, TinkerUi.DIM);

		ItemStack rock = menu.rock();
		boolean hasRock = !rock.isEmpty();
		int colour = hasRock ? Knapping.colour(rock) : 0xFF3A3A44;
		int cellHover = hasRock ? cellAt(mouseX, mouseY) : -1;
		int tile = tileAt(mouseX, mouseY);
		Knapping.Shape ghost = tile >= 0 ? shapes().get(tile) : null;
		TinkerUi.well(graphics, leftPos + GRID_X - 2, topPos + GRID_Y - 2, Knapping.SIDE * CELL + 4, Knapping.SIDE * CELL + 4);
		for (int cell = 0; cell < Knapping.CELLS; cell++) {
			int x = leftPos + GRID_X + cell % Knapping.SIDE * CELL, y = topPos + GRID_Y + cell / Knapping.SIDE * CELL;
			if (menu.chipOn(cell)) {
				graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, shade(colour, -40));
				graphics.fill(x + 1, y + 1, x + CELL - 2, y + CELL - 2, colour);
				graphics.fill(x + 1, y + 1, x + CELL - 2, y + 3, shade(colour, 30));
				if (cell == cellHover) {
					graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x40FFFFFF);
				}
			}
			if (ghost != null && ghost.has(cell)) {
				graphics.outline(x, y, CELL, CELL, TinkerUi.ACCENT);
			}
		}

		// start over
		boolean reset = hasRock && menu.chips() != Knapping.FULL;
		graphics.fill(leftPos + RESET_X, topPos + RESET_Y, leftPos + RESET_X + RESET_W, topPos + RESET_Y + RESET_H,
			reset && overReset(mouseX, mouseY) ? TinkerUi.ROW_HOVER : TinkerUi.ROW);
		graphics.centeredText(font, Component.translatable("gui.jaymospowers.knapping.start_over"), leftPos + RESET_X + RESET_W / 2, topPos + RESET_Y + 2,
			reset ? TinkerUi.ACCENT : TinkerUi.DIM);

		// the shapes
		int sx = sideX();
		TinkerUi.panel(graphics, sx, topPos, sideWidth(), sideHeight());
		graphics.text(font, Component.translatable("gui.jaymospowers.knapping.shapes"), sx + SIDE_PAD, topPos + 5, TinkerUi.ACCENT, false);
		List<Knapping.Shape> shapes = shapes();
		for (int i = 0; i < shapes.size(); i++) {
			int x = sx + SIDE_PAD + i % TILE_COLUMNS * TILE, y = topPos + SIDE_TOP + i / TILE_COLUMNS * TILE;
			graphics.fill(x, y, x + TILE - 1, y + TILE - 1, i == tile ? TinkerUi.ROW_HOVER : TinkerUi.ROW);
			graphics.item(Knapping.icon(shapes.get(i)), x + 1, y + 1);
		}
	}

	private static int shade(int argb, int by) {
		int r = Math.max(0, Math.min(255, (argb >> 16 & 255) + by)), g = Math.max(0, Math.min(255, (argb >> 8 & 255) + by)),
			b = Math.max(0, Math.min(255, (argb & 255) + by));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		TinkerUi.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY);
		if (menu.rock().isEmpty()) {
			graphics.centeredText(font, Component.translatable("gui.jaymospowers.knapping.put_rock"), GRID_X + Knapping.SIDE * CELL / 2, GRID_Y + 22,
				TinkerUi.DIM);
		}
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		int tile = tileAt(mouseX, mouseY);
		if (tile >= 0) {
			ItemStack made = Knapping.icon(shapes().get(tile));
			graphics.setTooltipForNextFrame(font, made.getHoverName(), mouseX, mouseY);
		} else if (overReset(mouseX, mouseY)) {
			graphics.setTooltipForNextFrame(font, Component.translatable("gui.jaymospowers.knapping.start_over.tip"), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int cell = menu.rock().isEmpty() ? -1 : cellAt(event.x(), event.y());
		if (cell >= 0 && menu.chipOn(cell)) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, cell);
			return true;
		}
		if (overReset(event.x(), event.y()) && !menu.rock().isEmpty() && menu.chips() != Knapping.FULL) {
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, KnappingMenu.START_OVER);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
		boolean onSide = mouseX >= sideX() && mouseX < sideX() + sideWidth() && mouseY >= topPos && mouseY < topPos + sideHeight();
		return !onSide && super.hasClickedOutside(mouseX, mouseY, left, top);
	}
}
