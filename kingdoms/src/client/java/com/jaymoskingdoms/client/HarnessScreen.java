package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Harness;
import com.jaymoskingdoms.HarnessMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The harness screen ({@link HarnessMenu}): the wearer in the middle; helmet, chestplate, leggings and boots down the
 * left with the hood, the over-garment, cuisses and greaves beside them; the crest, spaulders, arm harness and gauntlets
 * down the right with the gorget, mail, padding and tassets beside those. A bare place shows the first letter of its name, faintly,
 * and says what it is under the mouse. Drawn like the H screen: a dark panel with a gold stripe, sunken slots.
 */
public class HarnessScreen extends AbstractContainerScreen<HarnessMenu> {
	private static final int WIDTH = 176, HEIGHT = 204;
	/** The figure's well, and how big the wearer is drawn in it. */
	private static final int WELL_X = 46, WELL_Y = 17, WELL_W = 58, WELL_H = 72, FIGURE = 30;
	private float xMouse;
	private float yMouse;

	public HarnessScreen(HarnessMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
		this.inventoryLabelY = HarnessMenu.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		HarnessTab.add(this, true);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		this.xMouse = mouseX;
		this.yMouse = mouseY;
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		Ui.panel(graphics, x, y, WIDTH, HEIGHT, Ui.GOLD);
		Ui.slots(graphics, this.menu.slots, x, y);
		for (int i = 0; i < Harness.SLOTS; i++) {
			Slot slot = this.menu.slots.get(i);
			if (!slot.hasItem()) {
				String name = Component.translatable("harness.jaymoskingdoms.slot." + Harness.Slot.values()[i].key()).getString();
				if (!name.isEmpty()) {
					graphics.text(this.font, Component.literal(name.substring(0, 1)), x + slot.x + 5, y + slot.y + 4, Ui.HINT);
				}
			}
		}
		Ui.well(graphics, x + WELL_X, y + WELL_Y, WELL_W, WELL_H);
		if (this.minecraft.player != null) {
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x + WELL_X + 1, y + WELL_Y + 1, x + WELL_X + WELL_W - 1, y + WELL_Y + WELL_H - 1,
				FIGURE, 0.0625F, this.xMouse, this.yMouse, this.minecraft.player);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, Ui.GOLD);
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, Ui.DIM);
	}

	@Override
	protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		super.extractTooltip(graphics, mouseX, mouseY);
		Slot slot = this.hoveredSlot;
		ItemStack carried = this.menu.getCarried();
		if (slot != null && !slot.hasItem() && carried.isEmpty() && slot.index < Harness.SLOTS) {
			graphics.setTooltipForNextFrame(this.font, Component.translatable("harness.jaymoskingdoms.slot." + Harness.Slot.values()[slot.index].key()), mouseX, mouseY);
		}
	}
}
