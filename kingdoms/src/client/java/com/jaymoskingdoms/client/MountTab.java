package com.jaymoskingdoms.client;

import com.jaymoskingdoms.client.mixin.equestrian.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Horseman's swap button (the little arrow on the survival inventory, while you ride something with an inventory, that
 * swaps to the mount's inventory) as a tab on the inventory's left edge, under the harness tab. Horseman puts its button
 * by a number in its own config, and there it could end up under the powers, baubles and harness tabs; this tab presses
 * that same button, so Horseman still does the swap (and its Ctrl + inventory key still works). Horseman's own button is
 * hidden while the tab shows, and comes back where Horseman put it while the recipe book has pushed the inventory over
 * (the tabs hide then).
 */
public final class MountTab extends AbstractButton {
	static final int W = 26, H = 24;
	/** Out from the screen's left edge, and down from its top (under the harness tab, HarnessTab). */
	static final int OUT = 25, DOWN = 86;
	private static final int INVENTORY_W = 176;
	/** Horseman's swap button's size on the inventory (the recipe book's button is 20 x 18). */
	private static final int SWAP_W = 14, SWAP_H = 15;

	private final InventoryScreen screen;
	/** Horseman's own button: this tab presses it. */
	private final ImageButton swap;

	private MountTab(InventoryScreen screen, ImageButton swap) {
		super(0, 0, W, H, label());
		this.screen = screen;
		this.swap = swap;
		setTooltip(Tooltip.create(getMessage()));
	}

	static void init() {
		if (!FabricLoader.getInstance().isModLoaded("horseman")) {
			return;
		}
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof InventoryScreen inventory) {
				ImageButton swap = swapButton(inventory);
				if (swap != null) {
					MountTab tab = new MountTab(inventory, swap);
					tab.place();
					Screens.getWidgets(screen).add(tab);
					ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> tab.place());
				}
			}
		});
	}

	/** Horseman's swap button on {@code screen}, if it put one there (it does only while you ride something with an inventory). */
	private static @Nullable ImageButton swapButton(InventoryScreen screen) {
		for (GuiEventListener widget : screen.children()) {
			if (widget instanceof ImageButton button && button.getWidth() == SWAP_W && button.getHeight() == SWAP_H) {
				return button;
			}
		}
		return null;
	}

	/** What Horseman's button says: "<mount> Inventory [CTRL+E]". */
	private static Component label() {
		Minecraft minecraft = Minecraft.getInstance();
		Entity vehicle = minecraft.player == null ? null : minecraft.player.getVehicle();
		Component mount = vehicle != null ? vehicle.getName() : Component.translatable("gui.horseman.switch_inventory.button.to_mount.tooltip.mount");
		MutableComponent key = Component.literal(minecraft.options.keyInventory.getTranslatedKeyMessage().getString());
		return Component.translatable("gui.horseman.switch_inventory.button.to_mount.tooltip", mount, key);
	}

	void place() {
		AbstractContainerScreenAccessor at = (AbstractContainerScreenAccessor)this.screen;
		this.visible = at.jaymoskingdoms$leftPos() == (this.screen.width - INVENTORY_W) / 2;
		setPosition(at.jaymoskingdoms$leftPos() - OUT, at.jaymoskingdoms$topPos() + DOWN);
		// one swap button showing at a time: this tab, or (recipe book open) Horseman's own
		this.swap.visible = !this.visible;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		this.swap.onPress(input);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = getX(), y = getY();
		// a vanilla tab in the inventory's own greys, open on the right where it meets the inventory
		graphics.fill(x, y, x + W, y + H, 0xFF000000);
		graphics.fill(x + 1, y + 1, x + W, y + H - 1, 0xFF555555);
		graphics.fill(x + 1, y + 1, x + W - 2, y + H - 3, 0xFFFFFFFF);
		graphics.fill(x + 3, y + 3, x + W, y + H - 3, isHoveredOrFocused() ? 0xFFDADADA : 0xFFC6C6C6);
		graphics.item(new ItemStack(Items.SADDLE), x + 6, y + 4);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
