package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Downed;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * (0.14.1) A downed player's own screen ({@link Downed}): greyed while down, darker once they've passed out, with a
 * slow heartbeat. The "hold sneak to give up" line and its bar come from the server, over the hotbar.
 */
public final class DownedHud {
	/** The grey laid over the screen (RGB), and how thick (0-255) while down and once passed out. */
	static final int GREY = 0x2A2A2A, DOWN_ALPHA = 0x60, OUT_ALPHA = 0xC0;
	/** How long the grey takes to come over the screen (ticks). */
	static final int FADE_IN_TICKS = 20;
	/** Passed out: a heartbeat this often (ticks), this loud and this deep. */
	static final int HEARTBEAT_TICKS = 32;
	static final float HEARTBEAT_VOLUME = 0.7F, HEARTBEAT_PITCH = 0.6F;

	private static int downTicks;

	private DownedHud() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(DownedHud::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("downed_hud"), DownedHud::render);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || !Downed.isDowned(player)) {
			downTicks = 0;
			return;
		}
		downTicks++;
		if (Downed.passedOut(player) && downTicks % HEARTBEAT_TICKS == 0) {
			player.level().playLocalSound(player, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, HEARTBEAT_VOLUME, HEARTBEAT_PITCH);
		}
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !Downed.isDowned(player)) {
			return;
		}
		int alpha = Downed.passedOut(player) ? OUT_ALPHA : DOWN_ALPHA;
		alpha = alpha * Math.min(downTicks, FADE_IN_TICKS) / FADE_IN_TICKS;
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | GREY);
	}
}
