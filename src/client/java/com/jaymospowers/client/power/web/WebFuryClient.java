package com.jaymospowers.client.power.web;

import com.jaymospowers.client.hud.HudStyle;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.power.web.WebFury;
import com.jaymospowers.power.web.WebSymbiote;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/** The big suit's fury as a thin bar above the hotbar, only while the big suit is up. */
final class WebFuryClient {
	private static final int BAR_W = 80, BAR_H = 2, ROW = 12;
	private static final int COLOR = 0xFFC03828, FULL_COLOR = 0xFFFF7040;

	private WebFuryClient() {
	}

	static void renderHud(GuiGraphicsExtractor graphics, LocalPlayer player) {
		if (!WebSymbiote.bulked(player)) {
			return;
		}
		int fury = WebFury.fury(player);
		int[] at = HudZones.place(HudZones.FOCUS, "jaymospowers:web_fury", ROW, BAR_W, BAR_H + 4);
		if (at == null) {
			return;
		}
		boolean full = fury >= WebFury.MAX;
		int color = full ? HudStyle.mix(COLOR, FULL_COLOR, 0.5F + 0.5F * Mth.sin(player.tickCount * 0.5F)) : COLOR;
		HudStyle.bar(graphics, at[0], at[1] + 2, BAR_W, BAR_H, fury / (float)WebFury.MAX, color);
	}
}
