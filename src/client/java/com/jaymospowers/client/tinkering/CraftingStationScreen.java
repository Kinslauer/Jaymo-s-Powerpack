package com.jaymospowers.client.tinkering;

import com.jaymospowers.tinkering.table.CraftingStationMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The Crafting Station: a crafting table's layout, drawn like the other Tinkers tables, with the side inventory and tabs. */
public class CraftingStationScreen extends AbstractContainerScreen<CraftingStationMenu> {
	public CraftingStationScreen(CraftingStationMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 166);
		this.titleLabelX = 29;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		TinkerUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
		TableUi.draw(graphics, font, menu.extras(), leftPos, topPos);
		TinkerUi.slots(graphics, menu, leftPos, topPos, CraftingStationMenu.RESULT);
		TinkerUi.arrowRight(graphics, leftPos + 92, topPos + 45, 22, TinkerUi.DIM);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		TinkerUi.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		TableUi.tooltip(graphics, font, menu.extras(), leftPos, topPos, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return TableUi.click(minecraft, menu.containerId, menu.extras(), leftPos, topPos, event.x(), event.y()) || super.mouseClicked(event, doubleClick);
	}
}
