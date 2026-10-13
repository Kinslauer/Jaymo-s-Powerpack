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
 * <p>
 * The weaker the bond, the stronger it is: a line or a feeding at full bond only shadows the edges, at no bond it's
 * {@code EPISODE_MAX}. Under {@code AMBIENT_BOND} it never quite leaves: a faint vignette breathes at the edges and
 * blinks darker now and then, more often and more deeply as the bond drops. Full strength is kept for a weak bond's own
 * lines ({@link WebNet.Dread#full}) and every takeover.
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
	/** A line's (or a feeding's) strength, at full bond and at none; full lines and takeovers are 1. */
	private static final float EPISODE_MIN = 0.35F, EPISODE_MAX = 0.75F;
	/** Under this bond it lingers: at no bond, this strong between blinks, and a blink this much stronger. */
	private static final int AMBIENT_BOND = 60;
	private static final float AMBIENT_MAX = 0.3F, BLINK_MAX = 0.3F;
	/** Blinks: how long each is (ticks), and how long between them, from just under {@code AMBIENT_BOND} to no bond. */
	private static final int BLINK_TICKS = 5, BLINK_GAP_MAX = 240, BLINK_GAP_MIN = 40;
	/** A takeover's dread swells in and fades out over this long. */
	private static final float TAKEOVER_FADE = 10.0F;

	private static long clock;
	private static long start = -1L;
	private static long until = -1L;
	private static long nextGrowl;
	private static boolean full;
	private static float takeover;
	private static long blinkAt = -1L, nextBlink;

	private WebSymbioteDread() {
	}

	static void onPacket(WebNet.Dread payload) {
		int ticks = Mth.clamp(payload.ticks(), 1, MAX_TICKS);
		if (until < clock) {
			start = clock;
			full = false;
		}
		full |= payload.full();
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
		takeover = Mth.clamp(takeover + (WebSymbioteClient.inputLocked() ? 1.0F : -1.0F) / TAKEOVER_FADE, 0.0F, 1.0F);
		float low = weakness();
		if (low <= 0.0F) {
			nextBlink = 0L;
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (nextBlink == 0L || minecraft.player == null) {
			nextBlink = clock + BLINK_GAP_MAX;
		} else if (clock >= nextBlink) {
			blinkAt = clock;
			int gap = Math.round(Mth.lerp(low, BLINK_GAP_MAX, BLINK_GAP_MIN));
			nextBlink = clock + gap / 2 + minecraft.player.getRandom().nextInt(gap);
		}
	}

	static void clear() {
		start = -1L;
		until = -1L;
		nextGrowl = 0L;
		full = false;
		takeover = 0.0F;
		blinkAt = -1L;
		nextBlink = 0L;
	}

	/** How far under {@code AMBIENT_BOND} your bond is (0 above it, 1 at none), or 0 unbonded. */
	private static float weakness() {
		var player = Minecraft.getInstance().player;
		if (player == null || !com.jaymospowers.power.web.WebSymbiote.isBonded(player)) {
			return 0.0F;
		}
		int bond = com.jaymospowers.power.web.WebSymbiote.state(player).bond();
		return Mth.clamp(1.0F - bond / (float)AMBIENT_BOND, 0.0F, 1.0F);
	}

	/** A line's strength for your bond (full lines aside): faint at full bond, {@code EPISODE_MAX} at none. */
	private static float episodeStrength() {
		var player = Minecraft.getInstance().player;
		float bond = player == null ? 1.0F : com.jaymospowers.power.web.WebSymbiote.state(player).bond()
			/ (float)com.jaymospowers.power.web.WebSymbiote.BOND_MAX;
		return Mth.lerp(bond, EPISODE_MAX, EPISODE_MIN);
	}

	/** The HUD element. */
	static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float now = clock + delta.getGameTimeDeltaPartialTick(false);
		float strength = 0.0F;
		if (until >= 0L && clock < until + FADE_OUT) {
			float in = Mth.clamp((now - start) / FADE_IN, 0.0F, 1.0F);
			float out = 1.0F - Mth.clamp((now - until) / FADE_OUT, 0.0F, 1.0F);
			strength = in * out * (full ? 1.0F : episodeStrength());
		}
		// a takeover: all the way, as long as it has you
		strength = Math.max(strength, takeover);
		// (the lingering vignette never darkens the whole screen)
		float shade = strength;
		float low = weakness();
		if (low > 0.0F) {
			// a weak bond lingers at the edges, and blinks darker now and then
			float ambient = AMBIENT_MAX * low;
			float b = blinkAt < 0L ? 1.0F : (now - blinkAt) / BLINK_TICKS;
			if (b >= 0.0F && b < 1.0F) {
				ambient += BLINK_MAX * low * Mth.sin(b * Mth.PI);
			}
			strength = Math.max(strength, ambient);
		}
		if (strength <= 0.0F) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int dark = (int)(255 * DARKEN * shade);
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
