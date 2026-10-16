package com.jaymospowers.client.armory;

import com.jaymospowers.armory.BombGlider;
import com.jaymospowers.armory.BombGliderMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The bomb glider's hold, drawn like the Tinkering stations (a dark panel, a bronze stripe, sunken slots): the fuel slot
 * with a flame gauge of what's left in the burner, and the rack with its count out of {@link BombGlider#MAX_DYNAMITE}.
 * An empty slot shows faintly what goes in it.
 */
public class BombGliderScreen extends AbstractContainerScreen<BombGliderMenu> {
	static final int PANEL = 0xD0101018, ACCENT = 0xFFD0843A, TEXT = 0xFFFFFFFF, DIM = 0xFFA8A8B8, WELL_EDGE = 0xFF34343E, WELL = 0xFF0C0C12,
		GAUGE = 0xFFE8742A, GAUGE_TOP = 0xFFFFD25A;
	/** Laid over a ghost item so it reads as a faint outline. */
	static final int GHOST_FADE = 0xA80C0C12;
	/** The fuel gauge: right of the fuel slot, this tall. */
	static final int GAUGE_X = BombGliderMenu.FUEL_X + 22, GAUGE_W = 4, GAUGE_H = 16;

	public BombGliderScreen(BombGliderMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 166);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = this.leftPos, y = this.topPos;
		graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
		graphics.fill(x, y, x + this.imageWidth, y + 2, ACCENT);
		for (Slot slot : this.menu.slots) {
			if (slot.isActive()) {
				graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, WELL_EDGE);
				graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, WELL);
			}
		}
		// what goes where
		this.ghost(graphics, BombGlider.FUEL_SLOT, new ItemStack(Items.BLAZE_ROD));
		this.ghost(graphics, BombGlider.DYNAMITE_SLOT, new ItemStack(Items.TNT));
		// the burner's gauge
		int gx = x + GAUGE_X, gy = y + BombGliderMenu.FUEL_Y;
		graphics.fill(gx - 1, gy - 1, gx + GAUGE_W + 1, gy + GAUGE_H + 1, WELL_EDGE);
		graphics.fill(gx, gy, gx + GAUGE_W, gy + GAUGE_H, WELL);
		int max = this.menu.burnMax();
		if (max > 0 && this.menu.burn() > 0) {
			int h = Math.max(1, Math.round(GAUGE_H * Math.min(1.0F, this.menu.burn() / (float)max)));
			graphics.fill(gx, gy + GAUGE_H - h, gx + GAUGE_W, gy + GAUGE_H, GAUGE);
			graphics.fill(gx, gy + GAUGE_H - h, gx + GAUGE_W, gy + GAUGE_H - h + 1, GAUGE_TOP);
		}
	}

	private void ghost(GuiGraphicsExtractor graphics, int index, ItemStack look) {
		Slot slot = this.menu.getSlot(index);
		if (slot.hasItem()) {
			return;
		}
		int sx = this.leftPos + slot.x, sy = this.topPos + slot.y;
		graphics.fakeItem(look, sx, sy);
		graphics.fill(sx, sy, sx + 16, sy + 16, GHOST_FADE);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, ACCENT, false);
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, DIM, false);
		graphics.text(this.font, Component.translatable("gui.jaymospowers.bomb_glider.fuel"), BombGliderMenu.FUEL_X - 2, BombGliderMenu.FUEL_Y - 12, DIM,
			false);
		Component rack = Component.translatable("gui.jaymospowers.bomb_glider.rack");
		graphics.text(this.font, rack, BombGliderMenu.RACK_X + 8 - this.font.width(rack) / 2, BombGliderMenu.RACK_Y - 12, DIM, false);
		int count = this.menu.getSlot(BombGlider.DYNAMITE_SLOT).getItem().getCount();
		Component of = Component.translatable("gui.jaymospowers.bomb_glider.count", count, BombGlider.MAX_DYNAMITE);
		graphics.text(this.font, of, BombGliderMenu.RACK_X + 8 - this.font.width(of) / 2, BombGliderMenu.RACK_Y + 20, TEXT, false);
		if (this.menu.burn() > 0) {
			Component left = Component.translatable("gui.jaymospowers.bomb_glider.seconds", (this.menu.burn() + 19) / 20);
			graphics.text(this.font, left, GAUGE_X + GAUGE_W + 4, BombGliderMenu.FUEL_Y + 4, TEXT, false);
		}
	}
}
