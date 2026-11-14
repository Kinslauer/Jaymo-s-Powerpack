package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Harness;
import com.jaymoskingdoms.HeraldryArmour;
import com.jaymoskingdoms.client.mixin.equestrian.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A tab on the left edge of the survival inventory with a cuirass on it: it opens the harness ({@link HarnessScreen}).
 * On the harness screen the same place holds a chest tab back to the inventory. It hangs under the tabs Jaymo's Powers
 * puts on that edge, and is hidden while the recipe book has pushed the inventory over.
 */
public final class HarnessTab extends AbstractButton {
	static final int W = 26, H = 24;
	/** Out from the screen's left edge, and down from its top (under Powers' powers and baubles tabs). */
	static final int OUT = 25, DOWN = 60;
	private static final int INVENTORY_W = 176;

	private final AbstractContainerScreen<?> screen;
	/** True on the harness screen (back to the inventory), false on the inventory (to the harness). */
	private final boolean back;

	private HarnessTab(AbstractContainerScreen<?> screen, boolean back) {
		super(0, 0, W, H, Component.translatable(back ? "container.inventory" : "container.jaymoskingdoms.harness"));
		this.screen = screen;
		this.back = back;
		setTooltip(Tooltip.create(getMessage()));
	}

	static void init() {
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof InventoryScreen inventory) {
				add(inventory, false);
			}
		});
	}

	/** Puts the tab on {@code screen}. */
	static void add(AbstractContainerScreen<?> screen, boolean back) {
		HarnessTab tab = new HarnessTab(screen, back);
		tab.place();
		Screens.getWidgets(screen).add(tab);
		ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> tab.place());
	}

	void place() {
		AbstractContainerScreenAccessor at = (AbstractContainerScreenAccessor)this.screen;
		this.visible = at.jaymoskingdoms$leftPos() == (this.screen.width - INVENTORY_W) / 2;
		setPosition(at.jaymoskingdoms$leftPos() - OUT, at.jaymoskingdoms$topPos() + DOWN);
	}

	@Override
	public void onPress(InputWithModifiers input) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		if (this.back) {
			minecraft.player.closeContainer();
			minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
		} else if (ClientPlayNetworking.canSend(Harness.Open.TYPE)) {
			ClientPlayNetworking.send(new Harness.Open());
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
		graphics.item(this.back ? new ItemStack(Items.CHEST) : HeraldryArmour.stack("plate_cuirass"), x + 6, y + 4);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
