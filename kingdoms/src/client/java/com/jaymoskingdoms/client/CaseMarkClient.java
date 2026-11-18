package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.RogueExtras;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/**
 * A Thief's Case the Mark ({@link RogueExtras}): once the server says a mark has been read, two pips sit under the
 * crosshair for as long as it keeps saying so. The left is their guard (red: wary, green: at ease); the right their purse
 * (gold: worth the reach, grey: not).
 */
public final class CaseMarkClient {
	/** A pip's size, the gap between the two, how far under the crosshair's middle they sit (pixels), and how long they stay after the last word (ticks). */
	static final int PIP = 4, GAP = 3, BELOW = 9, STAY = 16;
	static final int WARY_RGB = 0xD04030, EASY_RGB = 0x58B858, WORTH_RGB = 0xF2C84A, POOR_RGB = 0x707070, EDGE = 0xC0000000;

	private static boolean wary, worth;
	private static int left;

	private CaseMarkClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(RogueExtras.Cased.TYPE, (payload, context) -> context.client().execute(() -> {
			wary = payload.wary();
			worth = payload.worth();
			left = STAY;
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> left = 0);
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (left > 0) {
				left--;
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosKingdoms.id("case_mark"), CaseMarkClient::hud);
	}

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (left <= 0 || minecraft.player == null || minecraft.player.isSpectator() || !minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}
		float a = Math.min(1.0F, left / 6.0F);
		int y = graphics.guiHeight() / 2 + BELOW;
		int x = graphics.guiWidth() / 2 - PIP - GAP / 2 - 1;
		pip(graphics, x, y, wary ? WARY_RGB : EASY_RGB, a);
		pip(graphics, x + PIP + GAP, y, worth ? WORTH_RGB : POOR_RGB, a);
	}

	private static void pip(GuiGraphicsExtractor graphics, int x, int y, int rgb, float a) {
		graphics.fill(x - 1, y - 1, x + PIP + 1, y + PIP + 1, ARGB.multiplyAlpha(EDGE, a));
		graphics.fill(x, y, x + PIP, y + PIP, ARGB.color(a, rgb));
	}
}
