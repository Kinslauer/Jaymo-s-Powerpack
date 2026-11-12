package com.jaymospowers.client.power.chrono;

import com.jaymospowers.client.hud.HudStyle;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.power.chrono.ChronoMeters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Calculation's bar ({@code ChronoCalculation}) on Chrono's meters: a small chip in the right dock (row 11, just over the
 * speedster's gear chip) that shows while the bar holds something or is being spent. Pale blue while it fills (gold
 * when full: every blow lands on a weak point), white and draining while it is spent on dodging.
 */
public final class ChronoCalcHud {
	static final int ROW = 11, WIDTH = 78, HEIGHT = 18;
	static final int FILL = 0xBFE8FF, FULL = 0xFFC860, RUN = 0xFFFFFF;

	private ChronoCalcHud() {
	}

	public static void render(GuiGraphicsExtractor graphics, LocalPlayer player) {
		Integer bar = player.getAttached(ChronoMeters.CALC), run = player.getAttached(ChronoMeters.CALC_RUN);
		if (bar == null && run == null) {
			HudZones.clear(HudZones.DOCK_RIGHT, "chrono_calc");
			return;
		}
		int[] at = HudZones.place(HudZones.DOCK_RIGHT, "chrono_calc", ROW, WIDTH, HEIGHT);
		if (at == null) {
			return;
		}
		boolean spending = run != null;
		boolean full = !spending && bar != null && bar >= 100;
		int colour = spending ? RUN : full ? FULL : FILL;
		HudStyle.plate(graphics, at[0], at[1], WIDTH, HEIGHT, HudStyle.PLATE, HudStyle.opaque(colour));
		String key = spending ? "hud.jaymospowers.chrono_calc_run" : full ? "hud.jaymospowers.chrono_calc_full" : "hud.jaymospowers.chrono_calc";
		HudStyle.text(graphics, Minecraft.getInstance().font, Component.translatable(key), at[0] + 4, at[1] + 3, HudStyle.TEXT);
		float share = (spending ? run : bar) / 100.0F;
		HudStyle.bar(graphics, at[0] + 4, at[1] + 12, WIDTH - 8, 3, share, colour);
	}
}
