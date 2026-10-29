package com.jaymospowers.client.baubles;

import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.baubles.BaublesMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The bauble slots, drawn in the survival inventory's own greys and bevels so it reads as part of it: the player in a
 * black box in the middle (following the mouse, as in the inventory), the amulet, rings and belt on the left, the head,
 * body and charm on the right. An empty slot shows a faint picture of what goes there.
 */
public class BaublesScreen extends AbstractContainerScreen<BaublesMenu> {
	static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, SHADE = 0xFF555555, EDGE = 0xFF000000, WELL = 0xFF8B8B8B, WELL_DARK = 0xFF373737,
		LABEL = 0xFF404040;
	/** Laid over a picture in an empty slot so it reads as faint. */
	static final int GHOST_FADE = 0xB08B8B8B;
	/** The player box: left, top, right, bottom. */
	static final int BOX_X0 = 30, BOX_Y0 = 8, BOX_X1 = 146, BOX_Y1 = 78;

	private float xMouse, yMouse;

	public BaublesScreen(BaublesMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 166);
		this.inventoryLabelY = BaublesMenu.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		BaublesTab.add(this, true);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		this.xMouse = mouseX;
		this.yMouse = mouseY;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = this.leftPos, y = this.topPos, w = this.imageWidth, h = this.imageHeight;
		// the vanilla panel: a black rim, a white top-left bevel and a grey bottom-right one
		graphics.fill(x + 1, y, x + w - 1, y + h, EDGE);
		graphics.fill(x, y + 1, x + w, y + h - 1, EDGE);
		graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL);
		graphics.fill(x + 1, y + 1, x + w - 3, y + 3, LIGHT);
		graphics.fill(x + 1, y + 1, x + 3, y + h - 3, LIGHT);
		graphics.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, SHADE);
		graphics.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, SHADE);
		graphics.fill(x + 3, y + 3, x + w - 3, y + h - 3, PANEL);
		for (Slot slot : this.menu.slots) {
			well(graphics, x + slot.x, y + slot.y);
		}
		for (int i = 0; i < Baubles.SIZE; i++) {
			Slot slot = this.menu.getSlot(i);
			if (!slot.hasItem()) {
				int sx = x + slot.x, sy = y + slot.y;
				graphics.fakeItem(new ItemStack(Baubles.picture(Baubles.SLOTS[i])), sx, sy);
				graphics.fill(sx, sy, sx + 16, sy + 16, GHOST_FADE);
			}
		}
		graphics.fill(x + BOX_X0, y + BOX_Y0, x + BOX_X1, y + BOX_Y1, EDGE);
		if (this.minecraft.player != null) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x + BOX_X0, y + BOX_Y0, x + BOX_X1, y + BOX_Y1, 30, 0.0625F,
				this.xMouse, this.yMouse, this.minecraft.player);
		}
	}

	/** A sunken slot, as the inventory texture draws them. */
	private static void well(GuiGraphicsExtractor graphics, int sx, int sy) {
		graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, LIGHT);
		graphics.fill(sx - 1, sy - 1, sx + 16, sy + 16, WELL_DARK);
		graphics.fill(sx, sy, sx + 16, sy + 16, WELL);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LABEL, false);
	}
}
