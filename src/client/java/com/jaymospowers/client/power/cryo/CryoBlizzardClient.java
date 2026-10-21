package com.jaymospowers.client.power.cryo;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.cryo.CryoBlizzard;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

/**
 * (0.14.0) The Blizzard's whiteout on screen: anyone inside someone else's Blizzard ({@link CryoBlizzard#STORM}) sees the
 * world through driving white, thickest near its heart; the one who called it sees through it, but for a faint veil.
 * The snow itself is the server's particles.
 */
final class CryoBlizzardClient {
	/** The white, how thick it gets at the heart of someone else's storm, and the caller's own veil. */
	static final int WHITE = 0xEEF4FA;
	static final float MOST = 0.72F;
	static final float OWN = 0.12F;
	/** It builds over this many ticks at the start. */
	static final int BUILD_TICKS = 40;

	private CryoBlizzardClient() {
	}

	static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("cryo_whiteout"), CryoBlizzardClient::render);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		long now = minecraft.level.getGameTime();
		float alpha = 0.0F;
		for (Player player : minecraft.level.players()) {
			Long from = player.getAttached(CryoBlizzard.STORM);
			if (from == null) {
				continue;
			}
			float build = Math.min(1.0F, Math.max(0.0F, (now - from) / (float)BUILD_TICKS));
			if (player == minecraft.player) {
				alpha = Math.max(alpha, OWN * build);
				continue;
			}
			double distance = player.distanceTo(minecraft.player);
			if (distance > CryoBlizzard.RADIUS) {
				continue;
			}
			float near = (float)Math.sqrt(1.0 - distance / CryoBlizzard.RADIUS);
			alpha = Math.max(alpha, MOST * (0.35F + 0.65F * near) * build);
		}
		if (alpha > 0.01F) {
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), (int)(alpha * 255.0F) << 24 | WHITE);
		}
	}
}
