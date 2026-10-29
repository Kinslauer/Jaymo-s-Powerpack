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
 * <p>
 * Every line it speaks ({@link WebNet.Dread#urgency} above 0) also sends tendrils of black pulsing in along the screen's
 * edges, then fading: a plain whisper a few faint ones, a takeover's warning more and brighter, and the near-death alert
 * (it takes over to save itself) the most, the longest and the brightest, their violet glow at its strongest; while it
 * runs for its life they stay at full, and while it fights you for the suit they stay up too. Drawn procedurally (chains
 * of soft squares from the edge inward, a glow under a black core), seeded per line so each one looks its own.
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
	/** A takeover's dread swells in and fades out over this long (swelling in {@code SELF_TAKEOVER_SPEED} times quicker: it seizes you). */
	private static final float TAKEOVER_FADE = 10.0F;
	/**
	 * Fighting its hold: with the struggle bar full the takeover's dread gives way by {@code STRUGGLE_CLEAR}, flickering
	 * by up to {@code STRUGGLE_FLICKER} as the bar swings, and pours back as it drains.
	 */
	private static final float STRUGGLE_CLEAR = 0.55F, STRUGGLE_FLICKER = 0.3F;
	/** The tendrils: how many at the least and most urgent; how far in they reach (share of the smaller screen side). */
	private static final int TENDRILS_MIN = 6, TENDRILS_MAX = 26;
	private static final float REACH_MIN = 0.1F, REACH_MAX = 0.3F;
	/** How long a line's tendrils last (ticks) at the least and most urgent; their swelling in and fading out. */
	private static final int TENDRIL_TICKS_MIN = 45, TENDRIL_TICKS_MAX = 110, TENDRIL_IN = 8, TENDRIL_OUT = 20;
	/** Squares down each tendril; its width at the root (gui pixels) at the least and most urgent; the glow's spread round it. */
	private static final int TENDRIL_STEPS = 14;
	private static final float WIDTH_MIN = 3.0F, WIDTH_MAX = 7.0F, GLOW_SPREAD = 2.5F;
	/** The pulse: how fast it throbs (radians a tick, faster when urgent) and how much of the reach it takes back. */
	private static final float TENDRIL_PULSE_MIN = 0.18F, TENDRIL_PULSE_MAX = 0.55F, TENDRIL_PULSE_DEPTH = 0.35F;
	/** The core's colour and alpha; the glow's colour from faint to urgent, and its alpha from faint to urgent (0-255). */
	private static final int CORE_COLOR = 0x0B0610, CORE_ALPHA = 0xE6;
	private static final int GLOW_FAINT = 0x5A2A80, GLOW_URGENT = 0xD86CFF, GLOW_ALPHA_MIN = 0x20, GLOW_ALPHA_MAX = 0x90;
	/** The glowing rim the most urgent lines light round the whole screen edge (alpha at urgency 1, and its width). */
	private static final int RIM_ALPHA = 0x70, RIM_WIDTH = 3;
	/** Held strength while it runs for its life, and while it fights you for the suit. */
	private static final float FLEE_URGENCY = 1.0F, FIGHT_URGENCY = 0.55F;

	private static long clock;
	private static long start = -1L;
	private static long until = -1L;
	private static long nextGrowl;
	private static boolean full;
	private static float takeover;
	private static long blinkAt = -1L, nextBlink;
	/** The tendrils of the line being spoken: when they came, until when, how urgent, and their seed. */
	private static long tendrilStart = -1L, tendrilUntil = -1L;
	private static float tendrilUrgency;
	private static long tendrilSeed;
	/** The strength the tendrils were held at last tick (running for its life, fighting you for the suit), to fade from. */
	private static float heldWas;

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
		if (payload.urgency() > 0.0F) {
			float urgency = Mth.clamp(payload.urgency(), 0.0F, 1.0F);
			int last = Math.round(Mth.lerp(urgency, TENDRIL_TICKS_MIN, TENDRIL_TICKS_MAX));
			if (tendrilUntil < clock) {
				// a fresh line: new tendrils
				tendrilStart = clock;
				tendrilUrgency = urgency;
				tendrilSeed = clock * 0x9E3779B97F4A7C15L + (long)(urgency * 1000.0F);
			} else {
				// on top of one still showing: as urgent as the more urgent of them
				tendrilUrgency = Math.max(tendrilUrgency, urgency);
			}
			tendrilUntil = Math.max(tendrilUntil, clock + last);
		}
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
		float held = held();
		if (heldWas > 0.0F && held <= 0.0F && tendrilUntil < clock) {
			// no longer held: they fade out rather than vanish
			tendrilStart = clock - TENDRIL_IN;
			tendrilUntil = clock;
			tendrilUrgency = heldWas;
		}
		heldWas = held;
		takeover = Mth.clamp(takeover + (WebSymbioteClient.inputLocked() || WebSymbioteClient.escaping()
			? com.jaymospowers.power.web.WebSymbiote.SELF_TAKEOVER_SPEED : -1.0F) / TAKEOVER_FADE, 0.0F, 1.0F);
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
		tendrilStart = -1L;
		tendrilUntil = -1L;
		tendrilUrgency = 0.0F;
		heldWas = 0.0F;
	}

	/** How strongly the tendrils are held up right now: running for its life, or fighting you for the suit (0: not). */
	private static float held() {
		return WebSymbioteClient.fleeing() ? FLEE_URGENCY : WebSymbioteClient.peelFight() ? FIGHT_URGENCY : 0.0F;
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

	/** How much of a takeover's dread is left as you fight it (1: all of it). */
	private static float struggling(float now) {
		var player = Minecraft.getInstance().player;
		float share = player == null ? 0.0F : WebSymbioteClient.struggleShare(player);
		if (share <= 0.0F) {
			return 1.0F;
		}
		float flicker = Mth.sin(now * 2.3F) * Mth.sin(now * 0.71F + 2.0F) * STRUGGLE_FLICKER * share;
		return Mth.clamp(1.0F - STRUGGLE_CLEAR * share + flicker, 0.2F, 1.0F);
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
		// a takeover: all the way, as long as it has you (giving way, flickering, as you fight your way out of it)
		strength = Math.max(strength, takeover * struggling(now));
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
		// (0.17.150) its mood keeps the edge faintly lit in its own colour
		strength = Math.max(strength, WebSymbioteMindUi.edge());
		// its voice: tendrils along the edges (held up while it runs for its life, or fights you for the suit)
		float urgency = 0.0F, life = 0.0F;
		if (tendrilUntil >= 0L && clock < tendrilUntil + TENDRIL_OUT) {
			float in = Mth.clamp((now - tendrilStart) / TENDRIL_IN, 0.0F, 1.0F);
			float out = 1.0F - Mth.clamp((now - tendrilUntil) / TENDRIL_OUT, 0.0F, 1.0F);
			life = in * out;
			urgency = tendrilUrgency;
		}
		float held = held();
		if (held > 0.0F && (life <= 0.0F || held >= urgency)) {
			urgency = Math.max(urgency, held);
			life = Math.max(life, 1.0F);
		}
		if (strength <= 0.0F && life <= 0.0F) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		if (strength <= 0.0F) {
			tendrils(graphics, width, height, now, urgency, life);
			return;
		}
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
		int vignette = WebSymbioteMindUi.vignette(VIGNETTE_COLOR);
		for (int i = 0; i < VIGNETTE_STEPS; i++) {
			int a = alpha * (VIGNETTE_STEPS - i) * (VIGNETTE_STEPS - i) / (VIGNETTE_STEPS * VIGNETTE_STEPS);
			if (a <= 0) {
				break;
			}
			int c = a << 24 | vignette;
			int o = i * step;
			graphics.fill(o, o, width - o, o + step, c);
			graphics.fill(o, height - o - step, width - o, height - o, c);
			graphics.fill(o, o + step, o + step, height - o - step, c);
			graphics.fill(width - o - step, o + step, width - o, height - o - step, c);
		}
		if (life > 0.0F) {
			tendrils(graphics, width, height, now, urgency, life);
		}
	}

	/**
	 * Tendrils pulsing in from the screen's edges: {@code urgency} sets how many, how far, how wide, how fast and how
	 * bright; {@code life} (0-1) swells them in and fades them out. Each is a chain of squares from a point on the edge
	 * inward, writhing side to side and tapering, a violet glow under a black core.
	 */
	private static void tendrils(GuiGraphicsExtractor graphics, int width, int height, float now, float urgency, float life) {
		int count = Math.round(Mth.lerp(urgency, TENDRILS_MIN, TENDRILS_MAX));
		float reachMax = Math.min(width, height) * Mth.lerp(urgency, REACH_MIN, REACH_MAX) * life;
		float speed = Mth.lerp(urgency, TENDRIL_PULSE_MIN, TENDRIL_PULSE_MAX);
		float rootWidth = Mth.lerp(urgency, WIDTH_MIN, WIDTH_MAX);
		int glowColor = lerpColor(WebSymbioteMindUi.glow(GLOW_FAINT, false), WebSymbioteMindUi.glow(GLOW_URGENT, true), urgency);
		float glowAlpha = Mth.lerp(urgency, GLOW_ALPHA_MIN, GLOW_ALPHA_MAX) * life;
		int coreAlpha = (int)(CORE_ALPHA * life);
		float perimeter = 2.0F * (width + height);
		for (int i = 0; i < count; i++) {
			long h = tendrilSeed + i * 0x632BE59BD9B4E019L;
			// spread round the edge, each nudged off its even spot
			float u = (i + 0.5F + (float)(hash(h) - 0.5) * 0.7F) / count * perimeter;
			u = (u % perimeter + perimeter) % perimeter;
			float x, y, nx, ny;
			if (u < width) {
				x = u; y = 0.0F; nx = 0.0F; ny = 1.0F;
			} else if (u < width + height) {
				x = width; y = u - width; nx = -1.0F; ny = 0.0F;
			} else if (u < 2.0F * width + height) {
				x = width - (u - width - height); y = height; nx = 0.0F; ny = -1.0F;
			} else {
				x = 0.0F; y = height - (u - 2.0F * width - height); nx = 1.0F; ny = 0.0F;
			}
			float phase = (float)(hash(h + 1) * Math.PI * 2.0);
			// each throbs inward on its own beat
			float beat = Mth.sin(now * speed + phase);
			float length = reachMax * (0.55F + 0.6F * (float)hash(h + 2)) * (1.0F - TENDRIL_PULSE_DEPTH + TENDRIL_PULSE_DEPTH * beat * beat);
			if (length < 2.0F) {
				continue;
			}
			float writhe = length * 0.16F;
			for (int k = 0; k <= TENDRIL_STEPS; k++) {
				float f = k / (float)TENDRIL_STEPS;
				float d = length * f;
				float side = Mth.sin(f * 3.4F + now * 0.21F + phase) * writhe * f;
				float px = x + nx * d - ny * side;
				float py = y + ny * d + nx * side;
				float size = Math.max(1.0F, Mth.lerp(f, rootWidth, 1.0F));
				// the glow round it, brightest at the root
				int ga = (int)(glowAlpha * (1.0F - 0.6F * f));
				if (ga > 0) {
					float g = size * 0.5F + GLOW_SPREAD * (1.0F + urgency);
					graphics.fill(Math.round(px - g), Math.round(py - g), Math.round(px + g), Math.round(py + g), ga << 24 | glowColor);
				}
				float c = size * 0.5F;
				int ca = (int)(coreAlpha * (1.0F - 0.35F * f));
				graphics.fill(Math.round(px - c), Math.round(py - c), Math.round(px + Math.max(1.0F, c)), Math.round(py + Math.max(1.0F, c)), ca << 24 | CORE_COLOR);
			}
		}
		// the most urgent light a glowing rim round the whole edge, throbbing
		int rim = (int)(RIM_ALPHA * urgency * urgency * life * (0.6F + 0.4F * Mth.sin(now * speed * 1.5F)));
		if (rim > 0) {
			int c = rim << 24 | glowColor;
			graphics.fill(0, 0, width, RIM_WIDTH, c);
			graphics.fill(0, height - RIM_WIDTH, width, height, c);
			graphics.fill(0, RIM_WIDTH, RIM_WIDTH, height - RIM_WIDTH, c);
			graphics.fill(width - RIM_WIDTH, RIM_WIDTH, width, height - RIM_WIDTH, c);
		}
	}

	private static int lerpColor(int from, int to, float t) {
		int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
		int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
		return r << 16 | g << 8 | b;
	}

	/** A steady random number 0-1 for {@code seed}. */
	private static double hash(long seed) {
		long z = seed * 0x9E3779B97F4A7C15L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		return (z >>> 11) * 0x1.0p-53;
	}
}
