package com.jaymospowers.client.power.web;

import com.jaymospowers.power.web.WebNet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * The symbiote pressing in on you (see {@link WebNet.Dread}): while it speaks, and all through a feeding, the screen
 * darkens, a blackish-purple vignette pulses round the edges like a slow heartbeat, and a low growl plays for you alone
 * (vanilla warden and ravager sounds at their lowest pitch). The vignette is drawn the way the wind's screen washes
 * are, in nested soft frames.
 */
final class WebSymbioteDread {
	/** How dark the whole screen gets (alpha 0-1), and the vignette's colour and strongest alpha (0-255). */
	private static final float DARKEN = 0.35F;
	private static final int VIGNETTE_COLOR = 0x1A0A26;
	private static final int VIGNETTE_ALPHA = 0xC8;
	/** The pulse: how fast (radians a tick) and how deep (share of the vignette that throbs). */
	private static final float PULSE_SPEED = 0.32F;
	private static final float PULSE_DEPTH = 0.4F;
	/** Ticks to swell in and to fade out. */
	private static final int FADE_IN = 6;
	private static final int FADE_OUT = 15;
	/** The frame's depth, as a share of the smaller screen side, and how many soft steps it's drawn in. */
	private static final float VIGNETTE_DEPTH = 0.3F;
	private static final int VIGNETTE_STEPS = 12;
	/** At most one growl this often (ticks), however many lines come at once; and its layers (volume, pitch). */
	private static final int GROWL_GAP = 40;
	private static final float GROWL_VOLUME = 0.9F, GROWL_PITCH = 0.5F;
	private static final float RUMBLE_VOLUME = 0.5F, RUMBLE_PITCH = 0.5F;
	/** Longest a single packet can hold it on (ticks). */
	private static final int MAX_TICKS = 400;

	private static long clock;
	private static long start = -1L;
	private static long until = -1L;
	private static long nextGrowl;

	private WebSymbioteDread() {
	}

	static void onPacket(WebNet.Dread payload) {
		int ticks = Mth.clamp(payload.ticks(), 1, MAX_TICKS);
		if (until < clock) {
			start = clock;
		}
		until = Math.max(until, clock + ticks);
		if (payload.growl() && clock >= nextGrowl) {
			nextGrowl = clock + GROWL_GAP;
			Minecraft minecraft = Minecraft.getInstance();
			var random = minecraft.player == null ? null : minecraft.player.getRandom();
			float wobble = random == null ? 0.0F : random.nextFloat() * 0.05F;
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_AMBIENT, GROWL_PITCH + wobble, GROWL_VOLUME));
			minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.RAVAGER_AMBIENT, RUMBLE_PITCH, RUMBLE_VOLUME));
		}
	}

	static void tick() {
		clock++;
	}

	static void clear() {
		start = -1L;
		until = -1L;
		nextGrowl = 0L;
	}

	/** The HUD element. */
	static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (until < 0L || clock >= until + FADE_OUT) {
			return;
		}
		float now = clock + delta.getGameTimeDeltaPartialTick(false);
		float in = Mth.clamp((now - start) / FADE_IN, 0.0F, 1.0F);
		float out = 1.0F - Mth.clamp((now - until) / FADE_OUT, 0.0F, 1.0F);
		float strength = in * out;
		if (strength <= 0.0F) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int dark = (int)(255 * DARKEN * strength);
		if (dark > 0) {
			graphics.fill(0, 0, width, height, dark << 24);
		}
		// a slow double beat, like something breathing on you
		float beat = Mth.sin(now * PULSE_SPEED);
		float pulse = 1.0F - PULSE_DEPTH + PULSE_DEPTH * beat * beat;
		int alpha = (int)(VIGNETTE_ALPHA * strength * pulse);
		int depth = Math.max(8, (int)(Math.min(width, height) * VIGNETTE_DEPTH * (0.9F + 0.1F * pulse)));
		int step = Math.max(1, depth / VIGNETTE_STEPS);
		for (int i = 0; i < VIGNETTE_STEPS; i++) {
			int a = alpha * (VIGNETTE_STEPS - i) * (VIGNETTE_STEPS - i) / (VIGNETTE_STEPS * VIGNETTE_STEPS);
			if (a <= 0) {
				break;
			}
			int c = a << 24 | VIGNETTE_COLOR;
			int o = i * step;
			graphics.fill(o, o, width - o, o + step, c);
			graphics.fill(o, height - o - step, width - o, height - o, c);
			graphics.fill(o, o + step, o + step, height - o - step, c);
			graphics.fill(width - o - step, o + step, width - o, height - o - step, c);
		}
	}
}
