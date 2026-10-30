package com.jaymoskingdoms.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * (0.7.364) Andy, on the life story's "Let fate decide" road: "make this a ki ghost instead of the character, implying
 * what is all within us". A figure of ki in place of you: a body of the player's proportions (head, body, arms, legs)
 * built of layered pale-blue light, a wide faint glow under a brighter core, its legs fading away like a ghost's tail,
 * two bright eyes, breathing slowly while wisps of ki drift up off it. Drawn flat in the screen, so it needs no model.
 */
final class KiGhost {
	/** The ki's colour, its core's (paler), and the eyes'. */
	static final int KI = 0xFF7FC8FF, CORE = 0xFFDCF2FF, EYES = 0xFFFFFFFF;
	/** Each layer's spread around the body (pixels at full size) and its light (alpha), outermost first. */
	static final float[] SPREAD = {5.0F, 2.5F, 0.0F};
	static final float[] LIGHT = {0.12F, 0.28F, 0.55F};
	/** A breath: how long (ms) and how far the light swells. */
	static final long BREATH_MS = 3200L;
	static final float BREATH = 0.25F;
	/** The wisps rising off it: how many, how long one takes to rise (ms), and how far it rises (share of the height). */
	static final int WISPS = 14;
	static final long WISP_MS = 2600L;
	static final float WISP_RISE = 0.35F;
	/** The body, in model pixels (as a player's): its height, and how much of it the box's height holds. */
	static final float BODY_H = 32.0F, FILL = 0.82F;

	private KiGhost() {
	}

	/** The ghost standing in the box {@code x0,y0}-{@code x1,y1}, feet on its foot. */
	static void draw(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
		long now = System.currentTimeMillis();
		float px = (y1 - y0) * FILL / BODY_H;
		float cx = (x0 + x1) / 2.0F, foot = y1 - (y1 - y0) * 0.04F, top = foot - BODY_H * px;
		float breath = 1.0F + BREATH * Mth.sin((float)(now % BREATH_MS) / BREATH_MS * Mth.TWO_PI);
		float bob = Mth.sin((float)(now % (BREATH_MS * 2)) / (BREATH_MS * 2) * Mth.TWO_PI) * px * 0.6F;
		top += bob;
		// the parts, in model pixels from the head's top and the middle: x, y, w, h
		float[][] parts = {
			{-4, 0, 8, 8},    // head
			{-4, 8, 8, 12},   // body
			{-8, 8, 4, 12},   // right arm
			{4, 8, 4, 12},    // left arm
		};
		for (int layer = 0; layer < SPREAD.length; layer++) {
			float spread = SPREAD[layer] * px / 2.0F;
			int color = layer == SPREAD.length - 1 ? CORE : KI;
			int alpha = Math.round(255 * Math.min(1.0F, LIGHT[layer] * breath));
			int c = ARGB.color(alpha, color);
			for (float[] p : parts) {
				box(graphics, cx + p[0] * px - spread, top + p[1] * px - spread, p[2] * px + spread * 2, p[3] * px + spread * 2, c);
			}
			// the legs thin into a tail of light
			float lx = cx - 4 * px - spread, ly = top + 20 * px - spread, lw = 8 * px + spread * 2, lh = 12 * px + spread;
			graphics.fillGradient(Math.round(lx), Math.round(ly), Math.round(lx + lw), Math.round(ly + lh), c, ARGB.color(0, color));
		}
		// the eyes
		int eye = ARGB.color(Math.round(255 * Math.min(1.0F, 0.75F * breath)), EYES);
		box(graphics, cx - 3 * px, top + 4 * px, px * 2, px, eye);
		box(graphics, cx + 1 * px, top + 4 * px, px * 2, px, eye);
		// wisps of ki rising off it, each on its own time and place
		for (int i = 0; i < WISPS; i++) {
			long offset = (long)(i * 7919L % WISP_MS);
			float t = (float)((now + offset) % WISP_MS) / WISP_MS;
			float sx = cx + ((i * 37 % 17) - 8) * px * 0.9F + Mth.sin(t * Mth.TWO_PI + i) * px;
			float sy = top + ((i * 53 % 29)) * px - t * (y1 - y0) * WISP_RISE;
			float size = px * (1.5F - t);
			int a = Math.round(255 * 0.5F * (1.0F - t) * Math.min(1.0F, t * 4.0F));
			if (size > 0.5F && a > 4 && sy > y0) {
				box(graphics, sx, sy, size, size, ARGB.color(a, i % 3 == 0 ? CORE : KI));
			}
		}
	}

	private static void box(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		graphics.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
	}
}
