package com.jaymoskitchen.client.farm;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.farm.Familiarity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;

/**
 * The hearts of a familiar farm animal ({@link Familiarity}): look at one and five little hearts show under the crosshair,
 * one filled in for every fifth of the way it has come, the last ones turning gold when it is familiar enough to breed.
 * Nothing shows on an animal that isn't a farm animal, or while a screen is open.
 */
public final class FamiliarityHud {
	private static final int FULL = 0xFFE04050, GOLD = 0xFFFFD54A, EMPTY = 0xFF3A3A44;
	private static final int HEARTS = 5, GAP = 9;

	private FamiliarityHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosKitchen.id("familiar"), FamiliarityHud::render);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.screen() != null) {
			return;
		}
		Entity target = minecraft.crosshairPickEntity;
		if (!(target instanceof Animal animal) || !animal.getType().builtInRegistryHolder().is(Familiarity.FARM_ANIMALS)) {
			return;
		}
		float familiar = Familiarity.of(animal);
		Font font = minecraft.font;
		int cx = graphics.guiWidth() / 2, y = graphics.guiHeight() / 2 + 12;
		int x = cx - (HEARTS * GAP) / 2;
		boolean breeds = familiar >= Familiarity.BREEDS;
		for (int i = 0; i < HEARTS; i++) {
			boolean filled = familiar >= (i + 1) * (1.0F / HEARTS) - 0.001F;
			boolean half = !filled && familiar >= (i + 0.5F) * (1.0F / HEARTS);
			int colour = filled ? (breeds ? GOLD : FULL) : half ? FULL & 0x80FFFFFF | 0x40000000 : EMPTY;
			graphics.text(font, "♥", x + i * GAP, y, colour, true);
		}
	}
}
