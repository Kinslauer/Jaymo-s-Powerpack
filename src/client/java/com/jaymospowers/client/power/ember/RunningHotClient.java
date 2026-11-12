package com.jaymospowers.client.power.ember;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.power.ember.EmberSoak;
import com.jaymospowers.power.ember.RunningHot;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The heat gauge ({@link RunningHot}) and, under it, the soak store ({@link EmberSoak}): drawn on the HUD in the right
 * dock like the other power bars, never in the action bar. The heat bar goes white and pulses at the top.
 */
public final class RunningHotClient {
	private static final int WIDTH = 62, ROW = 20;
	private static final int COLOR = 0xFF8A1E, HOT = 0xFFE9B0, SOAK = 0xE0623A;

	private RunningHotClient() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("running_hot"), RunningHotClient::render);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator() || !RunningHot.user(player)) {
			return;
		}
		int heat = RunningHot.heat(player);
		int store = EmberSoak.store(player);
		if (heat <= 0 && store <= 0) {
			return;
		}
		int[] at = HudZones.place(HudZones.DOCK_RIGHT, "jaymospowers:running_hot", 21, WIDTH, ROW);
		if (at == null) {
			return;
		}
		int x = at[0];
		int y = at[1];
		float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
		if (heat > 0) {
			boolean top = heat >= RunningHot.MAX;
			int color = top ? lerp(COLOR, HOT, 0.5F + 0.5F * Mth.sin(time * 0.5F)) : heat >= RunningHot.HOT_FROM ? HOT : COLOR;
			graphics.text(minecraft.font, Component.translatable("hud.jaymospowers.hot").withColor(color), x, y, 0xFFFFFFFF);
			bar(graphics, x, y + 9, heat / (float)RunningHot.MAX, color);
		}
		if (store > 0) {
			bar(graphics, x, y + 13, store / (float)EmberSoak.MAX, SOAK);
		}
	}

	private static void bar(GuiGraphicsExtractor graphics, int x, int y, float share, int color) {
		graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + 4, 0xB0000000);
		graphics.fill(x, y, x + WIDTH, y + 3, 0x90303030);
		graphics.fill(x, y, x + Mth.ceil(WIDTH * Mth.clamp(share, 0.0F, 1.0F)), y + 3, 0xFF000000 | color);
	}

	private static int lerp(int a, int b, float t) {
		int r = Mth.lerpInt(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
		int g = Mth.lerpInt(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
		int bl = Mth.lerpInt(t, a & 0xFF, b & 0xFF);
		return r << 16 | g << 8 | bl;
	}
}
