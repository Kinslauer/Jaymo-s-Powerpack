package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Absorption;
import com.jaymoskingdoms.Hemomancy;
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
 * (0.7.554) A Blood Sorcerer's reservoir ({@link Hemomancy#RESERVOIR}, gathered and thrown by {@code BloodBlasts}): a
 * thin upright bar just right of the hotbar (where a Brute's rage meter sits; a player is only one class), deep crimson,
 * brightening as it fills and throbbing like a heartbeat when it's full. Hidden while empty.
 * (0.7.570) Every other Sorcerer's reserve of their element ({@link Absorption#RESERVE}) shows in the same place, in the
 * element's colour, glowing slowly when it's full.
 */
public final class BloodReservoirHud {
	/** The bar's size and gap from the hotbar (gui pixels). */
	static final int WIDTH = 4, HEIGHT = 20, GAP = 3;

	/** The fill drawn, eased toward the real one so gathering and throwing slide rather than jump. */
	private static float shown;

	private BloodReservoirHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, JaymosKingdoms.id("blood_reservoir"), BloodReservoirHud::draw);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer self = minecraft.player;
		boolean blood = self != null && Hemomancy.bloodMage(self);
		if (self == null || self.isSpectator() || !blood && !Absorption.absorber(self)) {
			shown = 0.0F;
			return;
		}
		float fill = blood ? Math.min(1.0F, Hemomancy.pool(self) / Hemomancy.POOL_MAX) : Math.min(1.0F, Absorption.reserve(self) / Absorption.RESERVE_MAX);
		shown += (fill - shown) * 0.25F;
		if (Math.abs(fill - shown) < 0.002F) {
			shown = fill;
		}
		if (shown <= 0.0F) {
			return;
		}
		int x = graphics.guiWidth() / 2 + 91 + GAP, bottom = graphics.guiHeight() - 1;
		int top = bottom - HEIGHT;
		graphics.fill(x - 1, top - 1, x + WIDTH + 1, bottom + 1, 0xA0000000);
		int filled = Math.max(1, Math.round(HEIGHT * shown));
		if (!blood) {
			int rgb = Absorption.colour(Absorption.element(self));
			float glow = 1.0F;
			if (fill >= 1.0F) {
				float t = (self.tickCount + delta.getGameTimeDeltaPartialTick(false)) / 20.0F;
				glow = 0.85F + 0.15F * Mth.sin(t * Mth.PI);
			}
			float b = (0.55F + 0.45F * shown) * glow;
			graphics.fill(x, bottom - filled, x + WIDTH, bottom, ARGB.color(255, (int) (ARGB.red(rgb) * b), (int) (ARGB.green(rgb) * b), (int) (ARGB.blue(rgb) * b)));
			graphics.fill(x, bottom - filled, x + WIDTH, bottom - filled + 1, ARGB.color(255, Math.min(255, ARGB.red(rgb) + 70),
				Math.min(255, ARGB.green(rgb) + 70), Math.min(255, ARGB.blue(rgb) + 70)));
			return;
		}
		float beat = 1.0F;
		if (fill >= 1.0F) {
			// full: a slow heartbeat, two quick throbs and a rest
			float t = ((self.tickCount + delta.getGameTimeDeltaPartialTick(false)) % 24.0F) / 24.0F;
			beat = 0.8F + 0.2F * Math.max(Mth.sin(t * Mth.TWO_PI * 4.0F), 0.0F) * (t < 0.5F ? 1.0F : 0.0F);
		}
		int colour = ARGB.colorFromFloat(1.0F, (0.35F + 0.4F * shown) * beat, 0.02F, 0.04F + 0.04F * shown);
		graphics.fill(x, bottom - filled, x + WIDTH, bottom, colour);
		// a bright meniscus on top
		graphics.fill(x, bottom - filled, x + WIDTH, bottom - filled + 1, ARGB.colorFromFloat(1.0F, 0.85F * beat, 0.15F, 0.15F));
	}
}
