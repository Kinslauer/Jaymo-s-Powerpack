package com.jaymospowers.client.tinkering;

import com.jaymospowers.tinkering.fletching.FletchingMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The Fletching Station: head, shaft and fletching down the left, a lingering potion beside them, the arrows on the right. */
public class FletchingScreen extends AbstractContainerScreen<FletchingMenu> {
	public FletchingScreen(FletchingMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 166);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		TinkerUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
		TinkerUi.slots(graphics, menu, leftPos, topPos, FletchingMenu.RESULT);
		// the parts feed one arrow: a bracket from the three slots to the middle, then on to the result
		int x = leftPos + 52;
		graphics.fill(x + 3, topPos + 26, x + 5, topPos + 66, TinkerUi.DIM);
		for (int i = 0; i < 3; i++) {
			graphics.fill(x, topPos + 26 + i * 20, x + 5, topPos + 28 + i * 20, TinkerUi.DIM);
		}
		TinkerUi.arrowRight(graphics, leftPos + 58, topPos + 46, 20, TinkerUi.DIM);
		TinkerUi.arrowRight(graphics, leftPos + 104, topPos + 46, 28, TinkerUi.DIM);
		graphics.centeredText(font, Component.translatable("gui.jaymospowers.fletching.potion"), leftPos + 92, topPos + 26, TinkerUi.DIM);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		TinkerUi.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY);
	}
}
