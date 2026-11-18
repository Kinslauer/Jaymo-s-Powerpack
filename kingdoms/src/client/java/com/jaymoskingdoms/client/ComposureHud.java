package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Discipline;
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
 * A Warlord's Composure ({@link Discipline}, class trees): a thin upright bar just left of the hotbar, the mirror of the
 * Brute's rage bar on the right ({@link BruteRageHud}), steel-blue as it fills and a steady gold when full. Hidden while empty.
 */
public final class ComposureHud {
	/** The bar's size and gap from the hotbar (gui pixels). */
	static final int WIDTH = 3, HEIGHT = 20, GAP = 3;

	private ComposureHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, JaymosKingdoms.id("warlord_composure"), ComposureHud::draw);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer self = minecraft.player;
		if (self == null || self.isSpectator() || !Classes.is(self, Classes.PlayerClass.WARLORD)) {
			return;
		}
		float fill = Discipline.composure(self);
		if (fill <= 0.0F) {
			return;
		}
		int x = graphics.guiWidth() / 2 - 91 - GAP - WIDTH, bottom = graphics.guiHeight() - 1;
		int top = bottom - HEIGHT;
		graphics.fill(x - 1, top - 1, x + WIDTH + 1, bottom + 1, 0xA0000000);
		int filled = Math.max(1, Math.round(HEIGHT * Math.min(1.0F, fill)));
		int colour;
		if (fill >= 1.0F) {
			float glint = 0.85F + 0.15F * Mth.sin((self.tickCount + delta.getGameTimeDeltaPartialTick(false)) * 0.15F);
			colour = ARGB.colorFromFloat(1.0F, 0.91F * glint, 0.77F * glint, 0.42F * glint);
		} else {
			colour = ARGB.colorFromFloat(1.0F, 0.35F + 0.2F * fill, 0.5F + 0.2F * fill, 0.75F);
		}
		graphics.fill(x, bottom - filled, x + WIDTH, bottom, colour);
	}
}
