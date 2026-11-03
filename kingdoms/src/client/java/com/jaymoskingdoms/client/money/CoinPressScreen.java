package com.jaymoskingdoms.client.money;

import com.jaymoskingdoms.client.Ui;
import com.jaymoskingdoms.money.CoinPress;
import com.jaymoskingdoms.money.CoinPressMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * (0.7.487) The Coin Press's screen ({@link CoinPressMenu}), drawn like the H screen: a dark panel with a gold stripe, sunken
 * slots, an arrow from the metal to the coins, and under them what the realm's mint may still strike today (or "no die"
 * outside a realm, or "the mint is spent").
 */
public class CoinPressScreen extends AbstractContainerScreen<CoinPressMenu> {
	private static final int WIDTH = 176, HEIGHT = 166;

	public CoinPressScreen(CoinPressMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, WIDTH, HEIGHT);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int x = this.leftPos;
		int y = this.topPos;
		Ui.panel(graphics, x, y, WIDTH, HEIGHT, Ui.GOLD);
		Ui.slots(graphics, this.menu.slots, x, y);
		// the arrow from the metal to the coins
		int ay = y + 43;
		graphics.fill(x + 70, ay, x + 100, ay + 2, Ui.HINT);
		graphics.fill(x + 96, ay - 3, x + 98, ay + 5, Ui.HINT);
		graphics.fill(x + 98, ay - 1, x + 100, ay + 3, Ui.HINT);
		Component status;
		int color;
		switch (this.menu.status()) {
			case CoinPress.NO_DIE -> {
				status = Component.translatable("coin_press.jaymoskingdoms.no_die");
				color = Ui.BAD;
			}
			case CoinPress.SPENT -> {
				status = Component.translatable("coin_press.jaymoskingdoms.spent");
				color = Ui.BAD;
			}
			default -> {
				status = Component.translatable("coin_press.jaymoskingdoms.left", this.menu.remaining(), this.menu.cap());
				color = Ui.GOOD;
			}
		}
		graphics.centeredText(this.font, status, x + WIDTH / 2, y + 62, color);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, Ui.GOLD, false);
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, Ui.DIM, false);
	}
}
