package com.jaymoskitchen.client.stomach;

import com.jaymoskitchen.client.KitchenUi;
import com.jaymoskitchen.stomach.LunchBoxMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The lunch box's nine food slots, in the Kitchen's panel (as the plow's screen). */
public class LunchBoxScreen extends AbstractContainerScreen<LunchBoxMenu> {
	private static final int WIDTH = 176, HEIGHT = 166;

	public LunchBoxScreen(LunchBoxMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		KitchenUi.panel(graphics, x, y, WIDTH, HEIGHT, KitchenUi.ACCENT);
		KitchenUi.rule(graphics, x + 6, x + WIDTH - 6, y + this.inventoryLabelY - 4);
		KitchenUi.slots(graphics, this.menu.slots, x, y);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, KitchenUi.ACCENT);
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, KitchenUi.DIM);
	}
}
