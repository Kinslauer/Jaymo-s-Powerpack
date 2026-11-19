package com.jaymospowers.client.fishing;

import com.jaymospowers.client.UiStyle;
import com.jaymospowers.fishing.FishTackle;
import com.jaymospowers.fishing.TackleBenchMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

/**
 * The tackle bench's screen, in the pack's dark panel with a river-blue stripe: the rod on the left, the hook, the line
 * and the float in a column beside it (an empty slot shows a faint example of what goes there), and the rod as fitted on
 * the right.
 */
public class TackleBenchScreen extends AbstractContainerScreen<TackleBenchMenu> {
	private static final int ACCENT = 0xFF4F8FC0;
	/** Laid over a ghost item so it reads as a faint outline. */
	private static final int GHOST_FADE = 0xA80C0C12;

	public TackleBenchScreen(TackleBenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 166);
		this.inventoryLabelY = TackleBenchMenu.INVENTORY_Y - 11;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = this.leftPos, y = this.topPos;
		UiStyle.panel(graphics, x, y, this.imageWidth, this.imageHeight, ACCENT);
		UiStyle.slots(graphics, this.menu.slots, x, y);
		ItemStack[] ghosts = {new ItemStack(Items.FISHING_ROD), new ItemStack(FishTackle.BARBED_HOOK), new ItemStack(FishTackle.BRAIDED_LINE),
			new ItemStack(Items.DYE.pick(net.minecraft.world.item.DyeColor.RED))};
		for (int i = 0; i < ghosts.length; i++) {
			if (!this.menu.getSlot(i).hasItem()) {
				int sx = x + this.menu.getSlot(i).x, sy = y + this.menu.getSlot(i).y;
				graphics.fakeItem(ghosts[i], sx, sy);
				graphics.fill(sx, sy, sx + 16, sy + 16, GHOST_FADE);
			}
		}
		// the arrow from the parts to the rod
		int ax = x + 90, ay = y + TackleBenchMenu.AT[TackleBenchMenu.RESULT][1] + 4;
		graphics.fill(ax, ay, ax + 30, ay + 8, this.menu.getSlot(TackleBenchMenu.RESULT).hasItem() ? ACCENT : UiStyle.WELL_EDGE);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, ACCENT);
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, UiStyle.DIM);
	}
}
