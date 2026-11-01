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
 * <p>
 * (0.19.55) Bleeding out (ideas from Death Engine, rebuilt): a dark red ring closes in from the edges as their strength
 * fades ({@link Downed#strength}), and their heart is heard while still awake: racing on the first adrenaline, then
 * slowing and pounding louder as they weaken, slowest once passed out.
 */
public final class DownedHud {
	/** The grey laid over the screen (RGB), and how thick (0-255) while down and once passed out. */
	static final int GREY = 0x2A2A2A, DOWN_ALPHA = 0x60, OUT_ALPHA = 0xC0;
	/** How long the grey takes to come over the screen (ticks). */
	static final int FADE_IN_TICKS = 20;
	/** Passed out: a heartbeat this loud and this deep ((0.19.55) how often: {@link #BEAT_OUT}). */
	static final float HEARTBEAT_VOLUME = 0.7F, HEARTBEAT_PITCH = 0.6F;

	/**
	 * (0.19.55) The bleeding-out ring: its colour (RGB), how far in from the edges it reaches at full strength and spent
	 * (share of the screen's shorter side), how thick at the very edge (0-255) at full strength and spent, and the bands
	 * it's drawn in (more is smoother). It breathes in and out with the heartbeat by this much.
	 */
	static final int RING = 0x3A0000, RING_BANDS = 14;
	static final float RING_STRONG = 0.12F, RING_SPENT = 0.42F, RING_EDGE_STRONG = 70.0F, RING_EDGE_SPENT = 210.0F, RING_PULSE = 0.08F;
	/** (0.19.55) Awake: a heartbeat every this many ticks at full strength (racing) and spent, this loud at each. */
	static final float BEAT_STRONG = 14.0F, BEAT_SPENT = 44.0F, BEAT_VOLUME_STRONG = 0.3F, BEAT_VOLUME_SPENT = 0.65F;
	/** (0.19.55) Passed out: slower still. */
	static final float BEAT_OUT = 52.0F;

	/** (0.18.84) Knocked out: how long the black takes to come over the screen, and to lift as they come round (ticks). */
	static final float BLACK_IN_TICKS = 6.0F, BLACK_OUT_TICKS = 30.0F;

	private static int downTicks;
	/** (0.19.55) Ticks since the last heartbeat, and how far through the beat's swell the ring is (0-1). */
	private static float sinceBeat, beatSwell, beatSwellBefore;
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
		beatSwellBefore = beatSwell;
		if (player == null || !Downed.isDowned(player)) {
			downTicks = 0;
			sinceBeat = 0.0F;
			beatSwell = 0.0F;
			return;
		}
		downTicks++;
		beatSwell = Math.max(0.0F, beatSwell - 0.12F);
		if (player.hasAttached(Downed.KNOCKED)) {
			return;
		}
		// (0.19.55) racing at first, slowing as they weaken, slowest out cold
		boolean out = Downed.passedOut(player);
		float strength = Downed.strength(player);
		float every = out ? BEAT_OUT : net.minecraft.util.Mth.lerp(strength, BEAT_SPENT, BEAT_STRONG);
		if (++sinceBeat >= every) {
			sinceBeat = 0.0F;
			beatSwell = 1.0F;
			float volume = out ? HEARTBEAT_VOLUME : net.minecraft.util.Mth.lerp(strength, BEAT_VOLUME_SPENT, BEAT_VOLUME_STRONG);
			float pitch = out ? HEARTBEAT_PITCH : net.minecraft.util.Mth.lerp(strength, HEARTBEAT_PITCH, 0.8F);
			player.level().playLocalSound(player, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, volume, pitch);
		}
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null && Downed.isDowned(player)) {
			int alpha = Downed.passedOut(player) ? OUT_ALPHA : DOWN_ALPHA;
			alpha = alpha * Math.min(downTicks, FADE_IN_TICKS) / FADE_IN_TICKS;
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | GREY);
			if (!player.hasAttached(Downed.KNOCKED)) {
				float fade = Math.min(downTicks, FADE_IN_TICKS) / (float) FADE_IN_TICKS;
				float swell = beatSwellBefore + (beatSwell - beatSwellBefore) * delta.getGameTimeDeltaPartialTick(false);
				ring(graphics, Downed.strength(player), swell, fade);
			}
		}
	}

	/**
	 * (0.19.55) The bleeding-out ring: dark red, thickest at the edges and thinning inward, reaching further in and darker as
	 * {@code strength} fades, swelling a little on each heartbeat. Drawn as frames of bands, from the edge in.
	 */
	private static void ring(GuiGraphicsExtractor graphics, float strength, float swell, float fade) {
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		float weak = 1.0F - strength;
		float reach = Math.min(w, h) * net.minecraft.util.Mth.lerp(weak, RING_STRONG, RING_SPENT) * (1.0F + RING_PULSE * swell);
		float edge = net.minecraft.util.Mth.lerp(weak, RING_EDGE_STRONG, RING_EDGE_SPENT) * fade;
		float band = reach / RING_BANDS;
		for (int i = 0; i < RING_BANDS; i++) {
			float t = 1.0F - i / (float) RING_BANDS;
			int alpha = Math.round(edge * t * t);
			if (alpha <= 0) {
				continue;
			}
			int a = Math.round(i * band), b = Math.round((i + 1) * band);
			if (b <= a || a * 2 >= Math.min(w, h)) {
				continue;
			}
			int color = Math.min(255, alpha) << 24 | RING;
			// one frame: the top and bottom strips full width, the sides between them
			graphics.fill(a, a, w - a, b, color);
			graphics.fill(a, h - b, w - a, h - a, color);
			graphics.fill(a, b, b, h - b, color);
			graphics.fill(w - b, b, w - a, h - b, color);
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
