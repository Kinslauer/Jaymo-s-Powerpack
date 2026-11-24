package com.jaymospowers.client.tinkering.smithing;

import com.jaymospowers.client.tinkering.TinkerUi;
import com.jaymospowers.tinkering.smithing.DraftingTableMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (Smithing) The Drafting Table, drawn like the other Tinkering stations: a blueprint or a forged piece on the left, paper
 * under it (a faint sheet while it's empty), an arrow, and the copy or the pattern on the right.
 */
public class DraftingTableScreen extends AbstractContainerScreen<DraftingTableMenu> {
	/** Laid over a ghost item so it reads as a faint outline. */
	private static final int GHOST_FADE = 0xA80C0C12;

	public DraftingTableScreen(DraftingTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 166);
		this.inventoryLabelY = DraftingTableMenu.INVENTORY_Y - 11;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		TinkerUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
		TinkerUi.slots(graphics, menu, leftPos, topPos, DraftingTableMenu.RESULT);
		if (!menu.getSlot(DraftingTableMenu.PAPER).hasItem()) {
			int sx = leftPos + DraftingTableMenu.AT[DraftingTableMenu.PAPER][0], sy = topPos + DraftingTableMenu.AT[DraftingTableMenu.PAPER][1];
			graphics.fakeItem(new ItemStack(Items.PAPER), sx, sy);
			graphics.fill(sx, sy, sx + 16, sy + 16, GHOST_FADE);
		}
		boolean ready = menu.getSlot(DraftingTableMenu.RESULT).hasItem();
		TinkerUi.arrowRight(graphics, leftPos + 70, topPos + 46, 40, ready ? TinkerUi.ACCENT : TinkerUi.DIM);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		TinkerUi.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY);
	}
}
