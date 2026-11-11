package com.jaymospowers.client.power.chrono;

import com.jaymospowers.client.hud.HudStyle;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.power.chrono.ChronoGears;
import com.jaymospowers.power.chrono.ChronoMeters;
import com.jaymospowers.power.chrono.ChronoSpeed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The speedster's gear chip, bottom right in the right dock above the power's own block ({@code docs/HUD-Layout.md}, row 12):
 * "Gear 3" with five pips, and under it a thin bar for each meter running (held phase, slowed world, a shot being held) and
 * a line while a marked run is under way. Drawn by Chrono's HUD and Xenoform's, whichever the player has.
 */
public final class ChronoSpeedHud {
	static final int ROW = 12, WIDTH = 78, PIP = 6, PIP_GAP = 3;
	static final int GOLD = 0xFFFFC860, SKY = 0xFF8FD8FF;

	private ChronoSpeedHud() {
	}

	public static void render(GuiGraphicsExtractor graphics, LocalPlayer player) {
		Integer gear = player.getAttached(ChronoGears.GEAR);
		if (gear == null || !ChronoSpeed.flashing(player)) {
			HudZones.clear(HudZones.DOCK_RIGHT, "chrono_gear");
			return;
		}
		int colour = ChronoSpeed.velocity(player) ? SKY : GOLD;
		Integer phase = player.getAttached(ChronoMeters.PHASE), slow = player.getAttached(ChronoMeters.SLOW), caught = player.getAttached(ChronoMeters.CAUGHT);
		int bars = (phase != null ? 1 : 0) + (slow != null ? 1 : 0) + (caught != null ? 1 : 0);
		boolean run = ChronoSpeedKeys.running();
		int height = 13 + bars * 6 + (run ? 10 : 0);
		int[] at = HudZones.place(HudZones.DOCK_RIGHT, "chrono_gear", ROW, WIDTH, height);
		if (at == null) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int x = at[0], y = at[1];
		HudStyle.plate(graphics, x, y, WIDTH, height, HudStyle.PLATE, HudStyle.opaque(colour));
		HudStyle.text(graphics, font, Component.translatable("hud.jaymospowers.speed_gear", gear), x + 4, y + 3, HudStyle.TEXT);
		for (int i = 0; i < ChronoGears.GEARS; i++) {
			int px = x + WIDTH - 4 - (ChronoGears.GEARS - i) * (PIP + PIP_GAP) + PIP_GAP;
			graphics.fill(px, y + 4, px + PIP, y + 4 + 5, i < gear ? HudStyle.opaque(colour) : HudStyle.TRACK);
		}
		int by = y + 14;
		if (phase != null) {
			HudStyle.bar(graphics, x + 4, by, WIDTH - 8, 3, phase / 100.0F, 0xFFE8C0);
			by += 6;
		}
		if (slow != null) {
			HudStyle.bar(graphics, x + 4, by, WIDTH - 8, 3, slow / 100.0F, 0xBFE8FF);
			by += 6;
		}
		if (caught != null) {
			HudStyle.bar(graphics, x + 4, by, WIDTH - 8, 3, caught / 100.0F, 0xFFD54A);
			by += 6;
		}
		if (run) {
			HudStyle.text(graphics, font, Component.translatable("hud.jaymospowers.speed_run"), x + 4, by + 1, HudStyle.DIM);
		}
	}
}
