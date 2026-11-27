package com.jaymoskingdoms.client;

import com.jaymoskingdoms.VampireBlood;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * A full vampire's blood ({@link VampireBlood}) shown where the food bar was: a row of ten dark vials, each {@link VampireBlood#VIAL}
 * mB, the way the food bar's ten icons are laid (the first at the right, so it empties from the left). Plain boxes and fills, no
 * texture: a cork, a neck, a dark glass body with the blood standing in it. Under {@link VampireBlood#STARVE_BELOW} mB the row trembles
 * as an empty hunger bar does. With an inventory (or any container) open the exact mB shows on hover over the row. A dhampir, a mortal
 * and a werewolf keep the food bar; so does anyone riding a mount that has hearts (the mount's own bar takes the place).
 */
final class VampireBloodHud {
	/** The row's geometry, as the food bar's: ten, eight pixels apart, nine wide, the right edge 91 pixels from the middle, 39 up. */
	static final int VIALS = 10, STEP = 8, SIZE = 9, RIGHT = 91, UP = 39;
	/** Colours: the glass's edge, its empty inside, the cork, the blood, the blood's top edge, a glint on the glass. */
	static final int EDGE = 0xFF140609, INSIDE = 0xFF2B1A20, CORK = 0xFF6B4A2B, BLOOD = 0xFF8A0303, SURFACE = 0xFFB01216, GLINT = 0x40FFFFFF;
	/** The blood's height in a full vial (pixels). */
	static final int LEVEL = 5;

	private VampireBloodHud() {
	}

	static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, old -> (graphics, delta) -> {
			if (shown()) {
				row(graphics);
			} else {
				old.extractRenderState(graphics, delta);
			}
		});
		// the exact mB, on hover, with a container screen open
		ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
			if (screen instanceof AbstractContainerScreen<?>) {
				ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> hover(graphics, mouseX, mouseY));
			}
		});
	}

	/** Whether the vials take the food bar's place now. */
	private static boolean shown() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		return player != null && minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer() && VampireBlood.full(player)
			&& !(player.getVehicle() instanceof LivingEntity);
	}

	private static void row(GuiGraphicsExtractor graphics) {
		LocalPlayer player = Minecraft.getInstance().player;
		int mb = VampireBlood.get(player);
		boolean starving = mb < VampireBlood.STARVE_BELOW;
		int right = graphics.guiWidth() / 2 + RIGHT;
		int y = graphics.guiHeight() - UP;
		for (int i = 0; i < VIALS; i++) {
			int x = right - i * STEP - SIZE;
			int at = starving ? y + (player.tickCount * 3 + i * 7) % 3 - 1 : y;
			float share = Mth.clamp((mb - i * VampireBlood.VIAL) / (float)VampireBlood.VIAL, 0.0F, 1.0F);
			vial(graphics, x, at, share);
		}
	}

	/** One vial at {@code x}, {@code y}, {@code share} full (0 to 1). */
	private static void vial(GuiGraphicsExtractor graphics, int x, int y, float share) {
		graphics.fill(x + 3, y, x + 6, y + 1, CORK);
		graphics.fill(x + 3, y + 1, x + 6, y + 3, EDGE);
		graphics.fill(x + 1, y + 2, x + 8, y + 9, EDGE);
		graphics.fill(x + 2, y + 3, x + 7, y + 8, INSIDE);
		int height = Math.round(share * LEVEL);
		if (height > 0) {
			graphics.fill(x + 2, y + 8 - height, x + 7, y + 8, BLOOD);
			graphics.fill(x + 2, y + 8 - height, x + 7, y + 9 - height, SURFACE);
		}
		graphics.fill(x + 2, y + 3, x + 3, y + 6, GLINT);
	}

	private static void hover(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!shown()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		int right = graphics.guiWidth() / 2 + RIGHT;
		int left = right - (VIALS - 1) * STEP - SIZE;
		int top = graphics.guiHeight() - UP - 1;
		if (mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + SIZE + 2) {
			int mb = VampireBlood.get(player);
			Component tip = Component.translatable("hud.jaymoskingdoms.blood.tooltip", mb, VampireBlood.MAX);
			if (mb < VampireBlood.STARVE_BELOW) {
				tip = tip.copy().append(" ").append(Component.translatable("hud.jaymoskingdoms.blood.starving"));
			}
			graphics.setTooltipForNextFrame(tip, mouseX, mouseY);
		}
	}
}
