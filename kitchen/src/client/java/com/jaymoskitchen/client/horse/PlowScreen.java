package com.jaymoskitchen.client.horse;

import com.jaymoskitchen.client.KitchenUi;
import com.jaymoskitchen.horse.PlowMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The plow's three hoe slots (slot layout from NiftyCarts' plow screen, MIT, see LICENSE_niftycarts). (0.4.40)
 * Restyled like the H screen: a dark panel with the Kitchen's stripe, sunken slots, the three arms drawn as a bar
 * the hoes hang from.
 */
public class PlowScreen extends AbstractContainerScreen<PlowMenu> {
	private static final int WIDTH = 176, HEIGHT = 166;

	public PlowScreen(PlowMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		KitchenUi.panel(graphics, x, y, WIDTH, HEIGHT, KitchenUi.ACCENT);
		// the plow's beam, the three arms hanging from it down to their hoes
		graphics.fill(x + 50, y + 50, x + 126, y + 52, KitchenUi.WELL_EDGE);
		int[][] arms = {{65, 41}, {88, 34}, {111, 41}};
		for (int[] arm : arms) {
			graphics.fill(x + arm[0] - 1, y + arm[1], x + arm[0] + 1, y + 50, KitchenUi.WELL_EDGE);
		}
		KitchenUi.rule(graphics, x + 6, x + WIDTH - 6, y + this.inventoryLabelY - 4);
		KitchenUi.slots(graphics, this.menu.slots, x, y);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, KitchenUi.ACCENT, false);
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, KitchenUi.DIM, false);
	}
}
