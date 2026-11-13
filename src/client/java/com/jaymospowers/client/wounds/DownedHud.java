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
	/** (0.21.66) Passing out to turn: the eyes take this long to close (ticks); the soft edge of a lid (share of the screen). */
	static final float TURN_CLOSE_TICKS = 50.0F, LID_SOFT = 0.12F;
	/**
	 * (0.20.94) A death turned away (Mortality.WAKE_LINE): the black lifts this slowly, the line on it this big, and wrapped
	 * at this share of the screen's width.
	 */
	static final float WAKE_OUT_TICKS = 80.0F, LINE_SCALE = 1.5F, LINE_WIDTH = 0.6F;

	/**
	 * (0.21.42) Dying mid-turn: the vignette's reach (as {@link #ring}'s strength, lower reaches further), how bright, and one
	 * slow breath every this many ticks.
	 */
	static final float TURN_STRENGTH = 0.25F, TURN_GLOW = 1.1F, TURN_BREATH = 70.0F;

	/** Out of the body (LimboClient): the pale blue laid over the screen (RGB), and how thick (0-255). */
	static final int GHOST = 0x8ACEFF, GHOST_ALPHA = 0x26;

	/** (0.20.94) The line on the black now (a lang key), kept while the black lifts. */
	private static @org.jspecify.annotations.Nullable String line;

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
		// ((0.20.94) and black while a life that ended waits for the new character: Mortality.REBORNING)
		boolean reborning = player != null && player.isAlive() && player.hasAttached(com.jaymospowers.core.wounds.Mortality.REBORNING);
		// (out of their body, the black lifts: they look out of their ghost, LimboClient)
		boolean knocked = player != null && (player.hasAttached(Downed.KNOCKED) && !LimboClient.drifting() || reborning);
		String said = player == null ? null : reborning ? "message.jaymospowers.mortality.ended"
			: player.getAttached(com.jaymospowers.core.wounds.Mortality.WAKE_LINE);
		if (said != null) {
			line = said;
		}
		// ((0.21.66) passing out to turn, the eyes close slowly: Mortality.TURN_TINT)
		boolean turning = player != null && player.hasAttached(com.jaymospowers.core.wounds.Mortality.TURN_TINT);
		black = knocked ? Math.min(1.0F, black + 1.0F / (turning ? TURN_CLOSE_TICKS : BLACK_IN_TICKS))
			: Math.max(0.0F, black - 1.0F / (line != null ? WAKE_OUT_TICKS : BLACK_OUT_TICKS));
		if (black <= 0.0F) {
			line = null;
		}
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
		if (player != null && LimboClient.drifting()) {
			// a ghost's eyes: the world a little pale and blue, not the grey of the dying
			graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), GHOST_ALPHA << 24 | GHOST);
		} else if (player != null && Downed.isDowned(player)) {
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
		ring(graphics, strength, swell, fade, RING);
	}

	/** (0.21.42) {@link #ring} in {@code rgb}. */
	private static void ring(GuiGraphicsExtractor graphics, float strength, float swell, float fade, int rgb) {
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
			int color = Math.min(255, alpha) << 24 | rgb;
			// one frame: the top and bottom strips full width, the sides between them
			graphics.fill(a, a, w - a, b, color);
			graphics.fill(a, h - b, w - a, h - a, color);
			graphics.fill(a, b, b, h - b, color);
			graphics.fill(w - b, b, w - a, h - b, color);
		}
	}

	/**
	 * (0.21.66) Andy: "make sure the eyes animate closed too". The black comes as eyelids: a lid down from the top and one up
	 * from the bottom, soft at their edges, meeting in the middle as {@code dark} reaches 1 (and parting again as it lifts),
	 * the rest of the screen dimming behind them.
	 */
	private static void lids(GuiGraphicsExtractor graphics, float dark) {
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		if (dark >= 1.0F) {
			graphics.fill(0, 0, w, h, 0xFF000000);
			return;
		}
		float eased = dark * dark * (3.0F - 2.0F * dark);
		// behind the lids, the world dims as they close
		graphics.fill(0, 0, w, h, Math.round(dark * dark * 200.0F) << 24);
		float lid = h / 2.0F * eased;
		float soft = h * LID_SOFT;
		int solid = Math.max(0, Math.round(lid - soft));
		if (solid > 0) {
			graphics.fill(0, 0, w, solid, 0xFF000000);
			graphics.fill(0, h - solid, w, h, 0xFF000000);
		}
		// the lid's edge, fading out over LID_SOFT of the screen
		int bands = 8;
		for (int i = 0; i < bands; i++) {
			int a = solid + Math.round(soft * i / bands), b = solid + Math.round(soft * (i + 1) / bands);
			if (b <= a || a >= h / 2) {
				continue;
			}
			int alpha = Math.round(255.0F * (1.0F - (i + 0.5F) / bands));
			graphics.fill(0, a, w, Math.min(b, h / 2), alpha << 24);
			graphics.fill(0, Math.max(h - b, h / 2), w, h - a, alpha << 24);
		}
	}

	/** (0.18.84) Out cold: black. */
	private static void renderBlack(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float dark = blackBefore + (black - blackBefore) * delta.getGameTimeDeltaPartialTick(false);
		if (dark > 0.0F) {
			lids(graphics, dark);
		}
		// (0.21.42) dying mid-turn (Mortality.TURN_TINT): what they're becoming creeps in from the edges of the black, slowly
		// breathing, the colour of it
		LocalPlayer player = Minecraft.getInstance().player;
		Integer tint = player == null ? null : player.getAttached(com.jaymospowers.core.wounds.Mortality.TURN_TINT);
		if (tint != null && dark > 0.0F) {
			float t = (player.tickCount + delta.getGameTimeDeltaPartialTick(false)) / TURN_BREATH;
			float breath = 0.5F + 0.5F * net.minecraft.util.Mth.sin(t * net.minecraft.util.Mth.TWO_PI);
			ring(graphics, TURN_STRENGTH, breath, dark * TURN_GLOW, tint);
		}
		// (0.20.94) what happened, large in the middle of it, to be read before the world comes back
		int alpha = Math.round(dark * 255.0F);
		if (line != null && alpha > 8) {
			net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
			int width = Math.round(graphics.guiWidth() * LINE_WIDTH / LINE_SCALE);
			java.util.List<net.minecraft.util.FormattedCharSequence> rows = font.split(net.minecraft.network.chat.Component.translatable(line), width);
			graphics.pose().pushMatrix();
			graphics.pose().translate(graphics.guiWidth() / 2.0F, graphics.guiHeight() / 2.0F - rows.size() * font.lineHeight * LINE_SCALE / 2.0F);
			graphics.pose().scale(LINE_SCALE, LINE_SCALE);
			for (int i = 0; i < rows.size(); i++) {
				graphics.centeredText(font, rows.get(i), 0, i * font.lineHeight, alpha << 24 | 0xD8D8D8);
			}
			graphics.pose().popMatrix();
		}
	}
}
