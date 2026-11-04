package com.jaymospowers.client.jewellery;

import com.jaymospowers.baubles.BaubleItem;
import com.jaymospowers.client.tinkering.TinkerUi;
import com.jaymospowers.jewellery.Jewellery;
import com.jaymospowers.jewellery.JewellersBenchMenu;
import java.text.DecimalFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** (0.19.87) The Jeweller's Bench: a frame, a plus, a cut gem, an arrow and the set piece. */
public class JewellersBenchScreen extends AbstractContainerScreen<JewellersBenchMenu> {
	private static final int REFUSAL_COLOR = 0xFFE06060;
	private static final DecimalFormat NUMBER = new DecimalFormat("#.##");

	public JewellersBenchScreen(JewellersBenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 166);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		TinkerUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
		TinkerUi.slots(graphics, menu, leftPos, topPos, 2);
		// the plus between the frame and the gem
		int px = leftPos + 59, py = topPos + 55;
		graphics.fill(px - 4, py, px + 4, py + 1, TinkerUi.DIM);
		graphics.fill(px, py - 4, px + 1, py + 4, TinkerUi.DIM);
		boolean has = menu.getSlot(2).hasItem();
		TinkerUi.arrowRight(graphics, leftPos + 98, topPos + 55, 30, has ? TinkerUi.ACCENT : TinkerUi.DIM);
		Component refusal = menu.refusal();
		if (refusal != null) {
			graphics.text(font, refusal, leftPos + 8, topPos + 28, REFUSAL_COLOR, false);
		} else if (has) {
			Component line = bonusLine(menu.getSlot(2).getItem());
			if (line != null) {
				graphics.text(font, line, leftPos + 8, topPos + 28, TinkerUi.DIM, false);
			}
		}
	}

	private static Component bonusLine(ItemStack piece) {
		BaubleItem.Bonus bonus = Jewellery.bonus(piece);
		if (bonus == null) {
			return null;
		}
		boolean percent = bonus.operation() != AttributeModifier.Operation.ADD_VALUE;
		double amount = percent ? bonus.amount() * 100.0 : bonus.amount();
		String key = (amount >= 0 ? "attribute.modifier.plus." : "attribute.modifier.take.") + (percent ? "1" : "0");
		return Component.translatable(key, NUMBER.format(Math.abs(amount)), Component.translatable(bonus.attribute().value().getDescriptionId()));
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		TinkerUi.labels(graphics, font, title, titleLabelX, titleLabelY, playerInventoryTitle, inventoryLabelX, inventoryLabelY);
	}
}
