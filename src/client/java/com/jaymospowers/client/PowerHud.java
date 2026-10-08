package com.jaymospowers.client;

import com.jaymospowers.api.Power;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/** The power badge (icon + name, top-left), then the active power's own HUD. */
final class PowerHud {
	private PowerHud() {
	}

	static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator()) {
			return;
		}
		Power power = Powers.of(player);
		if (power == null) {
			return;
		}
		int right = 24 + minecraft.font.width(power.name()) + 4;
		int pips = 4 * 5;
		graphics.fill(2, 2, right + pips + 2, 22, 0x60000000);
		graphics.item(power.icon(), 4, 4);
		int accent = 0xFF000000 | power.color();
		graphics.text(minecraft.font, power.name(), 24, 8, accent);
		// one pip per ability: lit once unlocked; a thin bar under the badge fills toward the next one
		int unlocked = Mastery.unlocked(player);
		for (int i = 0; i < 4; i++) {
			int px = right + i * 5;
			graphics.fill(px, 10, px + 3, 13, i < unlocked ? accent : 0x50FFFFFF);
		}
		float progress = Mastery.progress(player);
		if (progress >= 0.0F) {
			graphics.fill(2, 22, right + pips + 2, 24, 0x60000000);
			graphics.fill(2, 22, 2 + Math.round((right + pips) * progress), 24, accent);
		}
		PowerClient client = PowerClients.get(power.id());
		if (client != null) {
			client.renderHud(graphics, delta, player);
		}
	}
}
