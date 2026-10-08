package com.jaymospowers.client;

import com.jaymospowers.api.Power;
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
		graphics.fill(2, 2, 24 + minecraft.font.width(power.name()) + 4, 22, 0x60000000);
		graphics.item(power.icon(), 4, 4);
		graphics.text(minecraft.font, power.name(), 24, 8, 0xFF000000 | power.color());
		PowerClient client = PowerClients.get(power.id());
		if (client != null) {
			client.renderHud(graphics, delta, player);
		}
	}
}
