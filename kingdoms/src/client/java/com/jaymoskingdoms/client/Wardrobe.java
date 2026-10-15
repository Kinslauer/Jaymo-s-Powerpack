package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.mixin.ContainerPosAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * (0.7.53) Andy: "a way to change clothes". A shirt button in the corner of the inventory's player box (and a key, unbound
 * until a player sets one) opens MCA's editor on its clothing page only ({@link WardrobeScreen}). Needs MCA.
 */
public final class Wardrobe {
	/** The button's size, and where it sits in the inventory: the player box's top right corner. */
	static final int SIZE = 11, X = 64, Y = 9;
	private static final int BORDER = 0xFF373737, FACE = 0xFFC6C6C6, FACE_HOVER = 0xFFFFFFFF, SHIRT = 0xFF3F5F9F;
	private static KeyMapping key;

	private Wardrobe() {
	}

	static void register() {
		if (!FabricLoader.getInstance().isModLoaded("mca")) {
			return;
		}
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("journal"));
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.wardrobe", InputConstants.UNKNOWN.getValue(), category));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (key.consumeClick()) {
				open(minecraft);
			}
		});
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof InventoryScreen inventory) {
				Button button = new Button(inventory);
				button.place();
				Screens.getWidgets(screen).add(button);
				// the recipe book moves the inventory about: follow it every frame
				ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> button.place());
			}
		});
	}

	static void open(Minecraft minecraft) {
		if (minecraft.player != null && !minecraft.player.isSpectator()) {
			minecraft.gui.setScreen(new WardrobeScreen(minecraft.player.getUUID()));
		}
	}

	/** The shirt in the inventory. */
	private static final class Button extends AbstractButton {
		private final InventoryScreen screen;

		Button(InventoryScreen screen) {
			super(0, 0, SIZE, SIZE, Component.translatable("button.jaymoskingdoms.wardrobe"));
			this.screen = screen;
			setTooltip(Tooltip.create(Component.translatable("button.jaymoskingdoms.wardrobe")));
		}

		void place() {
			ContainerPosAccessor at = (ContainerPosAccessor)this.screen;
			setPosition(at.jaymoskingdoms$leftPos() + X, at.jaymoskingdoms$topPos() + Y);
		}

		@Override
		public void onPress(InputWithModifiers input) {
			open(Minecraft.getInstance());
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			int x = getX();
			int y = getY();
			graphics.fill(x, y, x + SIZE, y + SIZE, BORDER);
			graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, isHoveredOrFocused() ? FACE_HOVER : FACE);
			// a T-shirt: sleeves across the top, the body below, a notch for the collar
			graphics.fill(x + 2, y + 3, x + SIZE - 2, y + 5, SHIRT);
			graphics.fill(x + 3, y + 5, x + SIZE - 3, y + SIZE - 2, SHIRT);
			graphics.fill(x + 4, y + 2, x + 5, y + 3, SHIRT);
			graphics.fill(x + SIZE - 5, y + 2, x + SIZE - 4, y + 3, SHIRT);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
