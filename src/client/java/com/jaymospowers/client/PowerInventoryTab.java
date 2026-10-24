package com.jaymospowers.client;

import com.jaymospowers.api.Power;
import com.jaymospowers.client.mixin.ContainerScreenAccessor;
import com.jaymospowers.core.Powers;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * (0.15.147) A tab on the survival inventory's left edge, with your power's icon: it opens the powers screen
 * ({@link PowerScreen}) on your power. It hides while the recipe book is open (the book sits where it would be).
 */
public final class PowerInventoryTab extends AbstractButton {
	static final int W = 26, H = 24;
	/** Out from the inventory's left edge, and down from its top. */
	static final int OUT = 25, DOWN = 8;
	/** The survival inventory's width: when it isn't centred, the recipe book has pushed it over. */
	private static final int INVENTORY_W = 176;

	private final InventoryScreen screen;

	private PowerInventoryTab(InventoryScreen screen) {
		super(0, 0, W, H, Component.translatable("screen.jaymospowers.title"));
		this.screen = screen;
		setTooltip(Tooltip.create(Component.translatable("screen.jaymospowers.inventory_tab")));
	}

	static void init() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof InventoryScreen inventory && minecraft.player != null && Powers.of(minecraft.player) != null) {
				PowerInventoryTab tab = new PowerInventoryTab(inventory);
				tab.place();
				Screens.getWidgets(screen).add(tab);
				ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> tab.place());
			}
		});
	}

	void place() {
		ContainerScreenAccessor at = (ContainerScreenAccessor)this.screen;
		this.visible = at.jaymospowers$leftPos() == (this.screen.width - INVENTORY_W) / 2;
		setPosition(at.jaymospowers$leftPos() - OUT, at.jaymospowers$topPos() + DOWN);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		Minecraft minecraft = Minecraft.getInstance();
		Power power = minecraft.player == null ? null : Powers.of(minecraft.player);
		if (power != null) {
			minecraft.gui.setScreen(new PowerScreen(power));
		}
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = getX(), y = getY();
		// a vanilla tab in the inventory's own greys, open on the right where it meets the inventory
		graphics.fill(x, y, x + W, y + H, 0xFF000000);
		graphics.fill(x + 1, y + 1, x + W, y + H - 1, 0xFF555555);
		graphics.fill(x + 1, y + 1, x + W - 2, y + H - 3, 0xFFFFFFFF);
		graphics.fill(x + 3, y + 3, x + W, y + H - 3, isHoveredOrFocused() ? 0xFFDADADA : 0xFFC6C6C6);
		Minecraft minecraft = Minecraft.getInstance();
		Power power = minecraft.player == null ? null : Powers.of(minecraft.player);
		if (power != null) {
			graphics.item(power.icon(), x + 6, y + 4);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
