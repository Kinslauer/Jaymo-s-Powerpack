package com.jaymoskingdoms.client;

import com.jaymoskingdoms.BruteRage;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * (0.7.517) A Brute's rage meter ({@link BruteRage}): a thin upright bar just right of the hotbar, dark red as it fills,
 * burning orange and throbbing while they rage, the rage's time left draining it. Hidden while empty.
 */
public final class BruteRageHud {
	/** The bar's size and gap from the hotbar (gui pixels). */
	static final int WIDTH = 3, HEIGHT = 20, GAP = 3;

	private BruteRageHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, JaymosKingdoms.id("brute_rage"), BruteRageHud::draw);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer self = minecraft.player;
		if (self == null || self.isSpectator() || !Classes.is(self, Classes.PlayerClass.BRUTE)) {
			return;
		}
		int left = BruteRage.raging(self);
		float fill = left > 0 ? Math.min(1.0F, left / (float) BruteRage.RAGE_TICKS) : BruteRage.meter(self);
		if (fill <= 0.0F) {
			return;
		}
		int x = graphics.guiWidth() / 2 + 91 + GAP, bottom = graphics.guiHeight() - 1;
		int top = bottom - HEIGHT;
		graphics.fill(x - 1, top - 1, x + WIDTH + 1, bottom + 1, 0xA0000000);
		int filled = Math.max(1, Math.round(HEIGHT * fill));
		int colour;
		if (left > 0) {
			float pulse = 0.75F + 0.25F * Mth.sin((self.tickCount + delta.getGameTimeDeltaPartialTick(false)) * 0.6F);
			colour = ARGB.colorFromFloat(1.0F, pulse, 0.45F * pulse, 0.05F);
		} else {
			colour = ARGB.colorFromFloat(1.0F, 0.45F + 0.4F * fill, 0.05F, 0.05F);
		}
		graphics.fill(x, bottom - filled, x + WIDTH, bottom, colour);
	}
}
