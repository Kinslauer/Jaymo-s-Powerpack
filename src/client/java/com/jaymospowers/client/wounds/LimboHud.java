package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Limbo;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Limbo on the player's own screen ({@link Limbo}): a black frame closing in from the edges, leaving the camera's near world
 * clear, with a few plain lines under it. Holding the Leave Limbo key (Backspace) fills a bar; full, the wait is over and the
 * full wipe goes ahead (as Reincarnate does).
 */
public final class LimboHud {
	// ---- tuning ----
	/** The frame's bands, how far in it reaches (share of the shorter side), how dark at the very edge (0-255) and at its inner edge. */
	static final int BANDS = 20;
	static final float REACH = 0.34F;
	static final int EDGE_ALPHA = 255, INNER_ALPHA = 40;
	/** The bar's length (characters) is drawn as a strip this wide (pixels). */
	static final int BAR_WIDTH = 120, BAR_HEIGHT = 4;

	private static KeyMapping leaveKey;
	private static int held;
	private static boolean sent;

	private LimboHud() {
	}

	public static void init() {
		leaveKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.limbo_leave", InputConstants.KEY_BACKSPACE,
			com.jaymospowers.client.JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(LimboHud::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.OVERLAY_MESSAGE, JaymosPowers.id("limbo_hud"), LimboHud::render);
	}

	/** The key's name as it is bound now. */
	public static Component keyName() {
		return leaveKey == null ? Component.empty() : leaveKey.getTranslatedKeyMessage();
	}

	private static boolean inLimbo(LocalPlayer player) {
		return player != null && player.hasAttached(Limbo.STATE);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (!inLimbo(player)) {
			held = 0;
			sent = false;
			return;
		}
		if (leaveKey.isDown()) {
			held++;
			if (held >= Limbo.LEAVE_TICKS && !sent) {
				sent = true;
				ClientPlayNetworking.send(new Limbo.Leave());
			}
		} else {
			held = Math.max(0, held - 2);
		}
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!inLimbo(minecraft.player)) {
			return;
		}
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		int reach = Math.round(Math.min(w, h) * REACH);
		// the black box: bands from the edge in, each thinner in dark
		for (int i = 0; i < BANDS; i++) {
			float t = i / (float) (BANDS - 1);
			int alpha = Math.round(EDGE_ALPHA + (INNER_ALPHA - EDGE_ALPHA) * t);
			int from = reach * i / BANDS, to = reach * (i + 1) / BANDS;
			int colour = alpha << 24;
			graphics.fill(from, from, w - from, to, colour);
			graphics.fill(from, h - to, w - from, h - from, colour);
			graphics.fill(from, to, to, h - to, colour);
			graphics.fill(w - to, to, w - from, h - to, colour);
		}
		graphics.fill(0, 0, w, reach / BANDS, 0xFF000000);
		var font = minecraft.font;
		graphics.centeredText(font, Component.translatable("limbo.jaymospowers.title"), w / 2, h - 58, 0xFFB8B8B8);
		graphics.centeredText(font, Component.translatable("limbo.jaymospowers.line"), w / 2, h - 46, 0xFF8A8A8A);
		graphics.centeredText(font, Component.translatable("limbo.jaymospowers.leave", keyName()), w / 2, h - 34, 0xFF6E6E6E);
		if (held > 0) {
			int x = (w - BAR_WIDTH) / 2, y = h - 20;
			graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF202020);
			graphics.fill(x, y, x + BAR_WIDTH * Math.min(held, Limbo.LEAVE_TICKS) / Limbo.LEAVE_TICKS, y + BAR_HEIGHT, 0xFFA0A0A0);
		}
	}
}
