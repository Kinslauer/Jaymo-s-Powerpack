package com.jaymospowers.client;

import com.jaymospowers.api.Power;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/** The power badge (icon + name, top-left), then the active power's own HUD. */
final class PowerHud {
	/** The corrupted badge's colour. */
	static final int CORRUPT_COLOR = 0x8A3FB5;
	/** Crack pixels (x, y pairs) over the badge's icon corner. */
	private static final int[] CRACKS = {5, 5, 6, 6, 7, 6, 8, 7, 9, 8, 9, 9, 10, 10, 12, 10, 13, 11, 14, 12, 8, 12, 7, 13, 6, 15, 15, 14, 16, 16, 17, 18};

	private PowerHud() {
	}

	/**
	 * The power's name as its fate has shaped it: lang {@code power.jaymospowers.<p>.ascended}, {@code .corrupted} and
	 * {@code .ascended_corrupted}, when the power defines them.
	 */
	static Component title(Power power, Fate.State fate) {
		String base = "power." + power.id().getNamespace() + "." + power.id().getPath();
		String suffix = fate.ascended() && fate.corrupted() ? ".ascended_corrupted" : fate.corrupted() ? ".corrupted" : fate.ascended() ? ".ascended" : null;
		if (suffix != null && Language.getInstance().has(base + suffix)) {
			return Component.translatable(base + suffix);
		}
		if (fate.ascended() && fate.corrupted() && Language.getInstance().has(base + ".corrupted")) {
			return Component.translatable(base + ".corrupted");
		}
		return power.name();
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
		Fate.State fate = Fate.state(player);
		Component name = title(power, fate);
		int right = 24 + minecraft.font.width(name) + 4;
		int pips = 4 * 5;
		graphics.fill(2, 2, right + pips + 2, 22, fate.corrupted() ? 0x90140A1E : 0x60000000);
		if (fate.ascended()) {
			// ascended: a gold rim
			graphics.outline(1, 1, right + pips + 2, 22, 0xFFFFD54A);
		}
		graphics.item(power.icon(), 4, 4);
		int accent = 0xFF000000 | (fate.corrupted() ? CORRUPT_COLOR : power.color());
		graphics.text(minecraft.font, name, 24, 8, accent);
		if (fate.corrupted()) {
			// corrupted: cracks across the badge
			for (int i = 0; i < CRACKS.length; i += 2) {
				graphics.fill(CRACKS[i], CRACKS[i + 1], CRACKS[i] + 1, CRACKS[i + 1] + 1, 0xC0000000);
			}
		}
		// one pip per ability: lit once unlocked; a thin bar under the badge fills toward the next one
		int unlocked = Mastery.unlocked(player);
		for (int i = 0; i < 4; i++) {
			int px = right + i * 5;
			graphics.fill(px, 10, px + 3, 13, i < unlocked ? accent : 0x50FFFFFF);
		}
		float progress = Mastery.progress(player);
		int barY = 22;
		if (progress >= 0.0F) {
			graphics.fill(2, barY, right + pips + 2, barY + 2, 0x60000000);
			graphics.fill(2, barY, 2 + Math.round((right + pips) * progress), barY + 2, accent);
			barY += 2;
		}
		// something creeping in: a dark sliver that grows (only once there's something to see)
		if (!fate.corrupted() && fate.corruption() > 0) {
			int width = Math.round((right + pips) * fate.corruption() / (float)Fate.MAX_CORRUPTION);
			graphics.fill(2, barY, 2 + width, barY + 1, 0xFF000000 | CORRUPT_COLOR);
		}
		PowerClient client = PowerClients.get(power.id());
		if (client != null) {
			client.renderHud(graphics, delta, player);
		}
	}
}
