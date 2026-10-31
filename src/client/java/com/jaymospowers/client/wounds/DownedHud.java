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
 * <p>
 * (0.18.84) Andy: "Players see a black screen and can mash to wake faster." Knocked out ({@link Downed#KNOCKED}: a rock,
 * too much poppy), the screen goes black; coming round, it fades back in. The "mash sneak" line comes from the server.
 */
public final class DownedHud {
	/** The grey laid over the screen (RGB), and how thick (0-255) while down and once passed out. */
	static final int GREY = 0x2A2A2A, DOWN_ALPHA = 0x60, OUT_ALPHA = 0xC0;
	/** How long the grey takes to come over the screen (ticks). */
	static final int FADE_IN_TICKS = 20;
	/** Passed out: a heartbeat this often (ticks), this loud and this deep. */
	static final int HEARTBEAT_TICKS = 32;
	static final float HEARTBEAT_VOLUME = 0.7F, HEARTBEAT_PITCH = 0.6F;

	/** (0.18.84) Knocked out: how long the black takes to come over the screen, and to lift as they come round (ticks). */
	static final float BLACK_IN_TICKS = 6.0F, BLACK_OUT_TICKS = 30.0F;

	private static int downTicks;
	/** (0.18.84) How black the screen is (0-1), last tick and this one. */
	private static float black, blackBefore;

	private DownedHud() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(DownedHud::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("downed_hud"), DownedHud::render);
		// (0.18.84) over the hotbar and bars too, under only the "mash sneak" line
		HudElementRegistry.attachElementBefore(VanillaHudElements.OVERLAY_MESSAGE, JaymosPowers.id("knocked_out_hud"), DownedHud::renderBlack);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		blackBefore = black;
		boolean knocked = player != null && player.hasAttached(Downed.KNOCKED);
		black = knocked ? Math.min(1.0F, black + 1.0F / BLACK_IN_TICKS) : Math.max(0.0F, black - 1.0F / BLACK_OUT_TICKS);
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
		if (player != null && Downed.isDowned(player)) {
			int alpha = Downed.passedOut(player) ? OUT_ALPHA : DOWN_ALPHA;
			alpha = alpha * Math.min(downTicks, FADE_IN_TICKS) / FADE_IN_TICKS;
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | GREY);
		}
	}

	/** (0.18.84) Out cold: black. */
	private static void renderBlack(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float dark = blackBefore + (black - blackBefore) * delta.getGameTimeDeltaPartialTick(false);
		if (dark > 0.0F) {
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Math.round(dark * 255.0F) << 24);
		}
	}
}
