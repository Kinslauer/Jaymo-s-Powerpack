package com.jaymospowers.client;

import com.jaymospowers.api.Power;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.client.hud.HudStyle;
import com.jaymospowers.client.hud.HudZones;
import com.jaymospowers.client.hud.PowerDock;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/** The power badge (icon + name, top-left under the minimap), then the active power's own HUD (the right dock's bottom row). */
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
	static Component title(Power power, Fate.State fate, net.minecraft.world.entity.player.Player player) {
		String base = "power." + power.id().getNamespace() + "." + power.id().getPath();
		// a power's own state comes first (Web's symbiote, say)
		String own = power.statusSuffix(player);
		if (own != null && Language.getInstance().has(base + own)) {
			return Component.translatable(base + own);
		}
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
		Component name = title(power, fate, player);
		int right = 24 + minecraft.font.width(name) + 4;
		int pips = 4 * 5;
		// (HUD rework) the badge heads the top-left rail, under Xaero's minimap (it sat on top of it)
		int[] at = HudZones.place(HudZones.RAIL, "jaymospowers:power_badge", 10, right + pips + 2, 25);
		if (at == null) {
			return;
		}
		int ox = at[0] - 2, oy = at[1] - 1;
		graphics.fill(ox + 2, oy + 2, ox + right + pips + 2, oy + 22, fate.corrupted() ? 0x90140A1E : HudStyle.PLATE_SOFT);
		if (fate.ascended()) {
			// ascended: a gold rim
			graphics.outline(ox + 1, oy + 1, right + pips + 2, 22, 0xFFFFD54A);
		}
		graphics.item(power.icon(), ox + 4, oy + 4);
		int accent = 0xFF000000 | (fate.corrupted() ? CORRUPT_COLOR : power.color());
		graphics.text(minecraft.font, name, ox + 24, oy + 8, accent, true);
		if (fate.corrupted()) {
			// corrupted: cracks across the badge
			for (int i = 0; i < CRACKS.length; i += 2) {
				graphics.fill(ox + CRACKS[i], oy + CRACKS[i + 1], ox + CRACKS[i] + 1, oy + CRACKS[i + 1] + 1, 0xC0000000);
			}
		}
		// one pip per ability: lit once unlocked; a thin bar under the badge fills toward the next one
		int unlocked = Mastery.unlocked(player);
		for (int i = 0; i < 4; i++) {
			int px = ox + right + i * 5;
			graphics.fill(px, oy + 10, px + 3, oy + 13, i < unlocked ? accent : 0x50FFFFFF);
		}
		float progress = Mastery.progress(player);
		int barY = oy + 22;
		if (progress >= 0.0F) {
			graphics.fill(ox + 2, barY, ox + right + pips + 2, barY + 2, HudStyle.PLATE_SOFT);
			graphics.fill(ox + 2, barY, ox + 2 + Math.round((right + pips) * progress), barY + 2, accent);
			barY += 2;
		}
		// something creeping in: a dark sliver that grows (only once there's something to see)
		if (!fate.corrupted() && fate.corruption() > 0) {
			int width = Math.round((right + pips) * fate.corruption() / (float)Fate.MAX_CORRUPTION);
			graphics.fill(ox + 2, barY, ox + 2 + width, barY + 1, 0xFF000000 | CORRUPT_COLOR);
		}
		PowerClient client = PowerClients.get(power.id());
		if (client != null) {
			// (HUD rework) the power's own block (chips, and its lines over them) is the right dock's bottom row
			if (!PowerDock.begin(client.hudHeight(player))) {
				return;
			}
			client.renderHud(graphics, delta, player);
		}
	}
}
