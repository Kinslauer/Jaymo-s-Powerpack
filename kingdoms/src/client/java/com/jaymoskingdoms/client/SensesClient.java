package com.jaymoskingdoms.client;

import com.jaymoskingdoms.client.hud.HudZones;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Senses;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * (0.7.111) The awareness gauge ({@link Senses}): a small eye above the crosshair. It fills (from the bottom) as the nearest
 * monster that is noticing you gets close to spotting you, opens red when one has spotted you, and half-closes (amber) while
 * one searches for where you went. The server sends its state a few times a second, only when it changes; the eye fades in
 * and out here. The sheet {@code textures/gui/awareness_eye.png} (made by {@code tools/gen_items.py}) has four 16x16
 * frames: empty, half-closed, open, full.
 * <p>(Medieval picks, Hearing) The stealth meter round it ({@link Senses.Meter}), bars and icons only: on the left how far
 * your noise carries (a bar to {@link #NOISE_FULL} blocks, pale, then amber, then red) with a mouth below it while you
 * whisper, talk or shout; on the right how lit you are (a bar to full light) with a drop of blood below it while you bleed.
 * It shows while the eye does, while you sneak, bleed or talk. Its icons are {@code textures/gui/stealth_meter.png}
 * ({@code tools/gen_items.py}): six 8x8 frames, noise, light, blood, whisper, talk, shout.
 */
public final class SensesClient {
	/** The eye's size, and how far above the crosshair's middle its bottom sits (pixels). */
	static final int SIZE = 16, ABOVE = 12;
	/** How fast it fades in and out (per tick). */
	static final float FADE = 0.12F;
	private static final Identifier EYE = JaymosKingdoms.id("textures/gui/awareness_eye.png");
	private static final int FRAMES = 4, EMPTY = 0, HALF = 1, OPEN = 2, FULL = 3;

	/** The meter: a side's width (an 8 pixel icon, a gap, a 3 pixel bar, a gap), and its icons' sheet. */
	static final int ICON = 8, BAR = 3, SIDE = ICON + 1 + BAR + 2;
	/** The noise bar is full at this many blocks; it turns amber and red past these. */
	static final int NOISE_FULL = 44, NOISE_AMBER = 10, NOISE_RED = 24;
	static final int NOISE_PALE_RGB = 0xD8D8D8, NOISE_AMBER_RGB = 0xE0A030, NOISE_RED_RGB = 0xD04030, LIGHT_RGB = 0xF2D27A, BAR_BACK = 0x80000000;
	private static final Identifier METER = JaymosKingdoms.id("textures/gui/stealth_meter.png");
	private static final int METER_FRAMES = 6, SOUND = 0, SUN = 1, BLOOD = 2, MOUTH = 3;

	/** What the server last said, and how visible the eye is now and a tick ago. */
	private static int state, fill;
	private static float alpha, lastAlpha;
	/** The meter as last sent, and how visible it is. */
	private static int noise, light, voice;
	private static boolean bleeding;
	private static float meterAlpha, lastMeterAlpha;

	private SensesClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Senses.Gauge.TYPE, (payload, context) -> context.client().execute(() -> {
			state = payload.state();
			fill = payload.fill();
		}));
		ClientPlayNetworking.registerGlobalReceiver(Senses.Meter.TYPE, (payload, context) -> context.client().execute(() -> {
			noise = payload.noise();
			light = payload.light();
			bleeding = payload.bleeding();
			voice = payload.voice();
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			state = fill = 0;
			alpha = lastAlpha = 0.0F;
			noise = light = voice = 0;
			bleeding = false;
			meterAlpha = lastMeterAlpha = 0.0F;
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			lastAlpha = alpha;
			alpha = state > 0 ? Math.min(1.0F, alpha + FADE) : Math.max(0.0F, alpha - FADE);
			lastMeterAlpha = meterAlpha;
			boolean shown = state > 0 || bleeding || voice > 0 || minecraft.player != null && minecraft.player.isShiftKeyDown();
			meterAlpha = shown ? Math.min(1.0F, meterAlpha + FADE) : Math.max(0.0F, meterAlpha - FADE);
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosKingdoms.id("awareness"), SensesClient::hud);
	}

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		float partial = delta.getGameTimeDeltaPartialTick(false);
		float a = Mth.lerp(partial, lastAlpha, alpha);
		float m = Mth.lerp(partial, lastMeterAlpha, meterAlpha);
		if (a <= 0.01F && m <= 0.01F || minecraft.player == null || minecraft.player.isSpectator()) {
			return;
		}
		// (HUD rework) above the crosshair, ABOVE row 10; the meter's sides either side of the eye
		int[] at = HudZones.place(HudZones.ABOVE, "jaymoskingdoms:awareness_eye", 10, SIZE + 2 * SIDE, SIZE);
		if (at == null) {
			return;
		}
		if (m > 0.01F) {
			meter(graphics, at[0], at[1], m);
		}
		if (a <= 0.01F) {
			return;
		}
		int x = at[0] + SIDE, y = at[1];
		int color = ARGB.white(a);
		switch (state) {
			case Senses.SPOTTED -> frame(graphics, OPEN, x, y, color);
			case Senses.SEARCHING -> frame(graphics, HALF, x, y, color);
			default -> {
				frame(graphics, EMPTY, x, y, color);
				// (the fill rises from the bottom of the full eye)
				int h = Mth.clamp(Math.round(SIZE * fill / (float) Senses.FILL_STEPS), 0, SIZE);
				if (h > 0) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, EYE, x, y + SIZE - h, 0.0F, (float) (FULL * SIZE + SIZE - h), SIZE, h, SIZE, SIZE * FRAMES, color);
				}
			}
		}
	}

	/** The meter's two sides, the eye's space between them, at {@code x}, {@code y}, as visible as {@code a}. */
	private static void meter(GuiGraphicsExtractor graphics, int x, int y, float a) {
		int white = ARGB.white(a);
		// left: the noise you make, and your voice
		icon(graphics, SOUND, x, y, white);
		if (voice > 0) {
			icon(graphics, MOUTH + Mth.clamp(voice, 1, 3) - 1, x, y + ICON, white);
		}
		int rgb = noise >= NOISE_RED ? NOISE_RED_RGB : noise >= NOISE_AMBER ? NOISE_AMBER_RGB : NOISE_PALE_RGB;
		bar(graphics, x + ICON + 1, y, Math.min(noise, NOISE_FULL) / (float) NOISE_FULL, rgb, a);
		// right: the light on you, and your blood
		int right = x + SIDE + SIZE + 2;
		bar(graphics, right, y, light / 15.0F, LIGHT_RGB, a);
		icon(graphics, SUN, right + BAR + 1, y, white);
		if (bleeding) {
			icon(graphics, BLOOD, right + BAR + 1, y + ICON, white);
		}
	}

	/** A bar {@link #SIZE} tall at {@code x}, {@code y}, filled from the bottom by {@code share}. */
	private static void bar(GuiGraphicsExtractor graphics, int x, int y, float share, int rgb, float a) {
		graphics.fill(x, y, x + BAR, y + SIZE, ARGB.multiplyAlpha(BAR_BACK, a));
		int h = Mth.clamp(Math.round(SIZE * share), 0, SIZE);
		if (h > 0) {
			graphics.fill(x, y + SIZE - h, x + BAR, y + SIZE, ARGB.color(a, rgb));
		}
	}

	private static void icon(GuiGraphicsExtractor graphics, int frame, int x, int y, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, METER, x, y, 0.0F, (float) (frame * ICON), ICON, ICON, ICON, ICON * METER_FRAMES, color);
	}

	private static void frame(GuiGraphicsExtractor graphics, int frame, int x, int y, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, EYE, x, y, 0.0F, (float) (frame * SIZE), SIZE, SIZE, SIZE, SIZE * FRAMES, color);
	}
}
