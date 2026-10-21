package com.jaymospowers.client.aether.gear;

import com.jaymospowers.aether.AetherItems;
import com.jaymospowers.aether.gear.AltarBlockEntity;
import com.jaymospowers.aether.gear.AltarMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The altar's screen, in the same dark panel as the pack's other stations with a golden stripe: the input in the middle of
 * a ring of eight shard slots (the ones the current recipe asks for are ringed in gold), an arrow that fills as the work
 * goes, and the output on the right. Empty shard slots show a faint shard.
 */
public class AltarScreen extends AbstractContainerScreen<AltarMenu> {
	static final int PANEL = 0xD0101018, ACCENT = 0xFFE8C24A, TEXT = 0xFFFFFFFF, DIM = 0xFFA8A8B8, WELL_EDGE = 0xFF34343E, WELL = 0xFF0C0C12,
		NEED_EDGE = 0xFFE8C24A, ARROW = 0xFF6F3DB8, ARROW_TOP = 0xFFBB8BF5;
	/** Laid over a ghost item so it reads as a faint outline. */
	static final int GHOST_FADE = 0xA80C0C12;
	/** The progress arrow: left, top, width, height. */
	static final int ARROW_X = 104, ARROW_Y = 44, ARROW_W = 28, ARROW_H = 8;

	public AltarScreen(AltarMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 184);
		this.inventoryLabelY = INVENTORY_LABEL_Y;
	}

	private static final int INVENTORY_LABEL_Y = AltarMenu.INVENTORY_Y - 11;

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = this.leftPos, y = this.topPos;
		graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
		graphics.fill(x, y, x + this.imageWidth, y + 2, ACCENT);
		int needed = this.menu.needed();
		for (Slot slot : this.menu.slots) {
			if (!slot.isActive()) {
				continue;
			}
			int index = slot.index;
			boolean asked = index >= AltarBlockEntity.FUEL_FIRST && index < AltarBlockEntity.FUEL_FIRST + needed;
			graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, asked ? NEED_EDGE : WELL_EDGE);
			graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, WELL);
		}
		// what goes where
		for (int i = 0; i < AltarBlockEntity.FUEL_COUNT; i++) {
			Slot slot = this.menu.getSlot(AltarBlockEntity.FUEL_FIRST + i);
			if (!slot.hasItem()) {
				int sx = x + slot.x, sy = y + slot.y;
				graphics.fakeItem(new ItemStack(AetherItems.AMBROSIUM_SHARD), sx, sy);
				graphics.fill(sx, sy, sx + 16, sy + 16, GHOST_FADE);
			}
		}
		// the arrow
		int ax = x + ARROW_X, ay = y + ARROW_Y;
		graphics.fill(ax - 1, ay - 1, ax + ARROW_W + 1, ay + ARROW_H + 1, WELL_EDGE);
		graphics.fill(ax, ay, ax + ARROW_W, ay + ARROW_H, WELL);
		int total = this.menu.total();
		if (total > 0 && this.menu.progress() > 0) {
			int w = Math.max(1, Math.round(ARROW_W * Math.min(1.0F, this.menu.progress() / (float)total)));
			graphics.fill(ax, ay, ax + w, ay + ARROW_H, ARROW);
			graphics.fill(ax, ay, ax + w, ay + 1, ARROW_TOP);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, ACCENT, false);
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, DIM, false);
		int needed = this.menu.needed();
		Component line = needed > 0 ? Component.translatable("gui.jaymospowers.altar.shards", needed) : Component.translatable("gui.jaymospowers.altar.idle");
		graphics.text(this.font, line, 44, 78, needed > 0 ? TEXT : DIM, false);
	}
}
