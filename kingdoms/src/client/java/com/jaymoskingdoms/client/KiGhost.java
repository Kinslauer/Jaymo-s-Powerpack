package com.jaymoskingdoms.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * (0.7.364) Andy, on the life story's "Let fate decide" road: "make this a ki ghost instead of the character, implying
 * what is all within us". A figure of ki in place of you: a body of the player's proportions (head, body, arms, legs)
 * built of layered pale-blue light, a wide faint glow under a brighter core, its legs fading away like a ghost's tail,
 * two bright eyes, breathing slowly while wisps of ki drift up off it. Drawn flat in the screen, so it needs no model.
 * <p>
 * Below the waist it is one tapering tail, as Wisp's and the grave ghost's are (Andy: "same with wisps tail, it should sway
 * with your movement"): it wavers while the pointer is still, and streams out the other way when the pointer moves, each
 * link following the one above it a little late so a stop runs down it as a wave and the tip arrives last.
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
	/** The tail's links, top to tip: width and length (model pixels). */
	static final float[][] TAIL = {{8, 1.5F}, {7, 1.5F}, {6, 1.5F}, {5, 1.5F}, {4, 1.5F}, {3, 1.5F}, {2, 1.5F}, {1, 2.0F}};
	/** How hard the pointer's speed (screen pixels a second) swings the tail, and the most it swings (radians). */
	static final float STREAM = 0.003F, SWING_MAX = 1.1F;
	/** How fast (per second) the top link takes up the swing, and each link below the one above it. */
	static final float ROOT_FOLLOW = 9.0F, LINK_FOLLOW = 7.0F;
	/** The waver while still: side to side at the tip (radians), its period (ms), and how far out of step link to link. */
	static final float WAVE = 0.25F, WAVE_STEP = 0.8F;
	static final long WAVE_MS = 2400L;

	/** The tail as it was last drawn: each link's swing, and where the pointer was and when. */
	private static final float[] SWING = new float[TAIL.length];
	private static float lastMouse = Float.NaN;
	private static long lastAt;

	private KiGhost() {
	}

	/** The ghost standing in the box {@code x0,y0}-{@code x1,y1}, feet on its foot. */
	static void draw(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float mouseX) {
		draw(graphics, x0, y0, x1, y1, mouseX, KI, CORE, EYES);
	}

	/** In the colours given; {@code mouseX} is where the pointer is (its movement swings the tail). */
	static void draw(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, float mouseX, int ki, int core, int eyes) {
		long now = System.currentTimeMillis();
		swing(now, mouseX);
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
			int color = layer == SPREAD.length - 1 ? core : ki;
			int alpha = Math.round(255 * Math.min(1.0F, LIGHT[layer] * breath));
			int c = ARGB.color(alpha, color);
			for (float[] p : parts) {
				box(graphics, cx + p[0] * px - spread, top + p[1] * px - spread, p[2] * px + spread * 2, p[3] * px + spread * 2, c);
			}
			// below the waist, a tail of light that thins and fades to its tip
			float tx = cx, ty = top + 20 * px, angle = 0.0F;
			for (int i = 0; i < TAIL.length; i++) {
				float along = i / (float)(TAIL.length - 1);
				angle += SWING[i] / TAIL.length * 2.0F
					+ WAVE / TAIL.length * Mth.sin((float)(now % WAVE_MS) / WAVE_MS * Mth.TWO_PI - i * WAVE_STEP) * (0.3F + 0.7F * along);
				float len = TAIL[i][1] * px, w = TAIL[i][0] * px + spread * 2;
				int la = Math.round(alpha * (1.0F - 0.75F * along));
				box(graphics, tx - w / 2.0F, ty - (i == 0 ? spread : 0.0F), w, len + 1 + (i == 0 ? spread : 0.0F), ARGB.color(la, color));
				tx += Mth.sin(angle) * len;
				ty += Mth.cos(angle) * len;
			}
		}
		// the eyes
		int eye = ARGB.color(Math.round(255 * Math.min(1.0F, 0.75F * breath)), eyes);
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
				box(graphics, sx, sy, size, size, ARGB.color(a, i % 3 == 0 ? core : ki));
			}
		}
	}

	/** Steps the tail's swing to now: the top link leans away from the pointer's movement, each link below follows late. */
	private static void swing(long now, float mouseX) {
		float dt = Mth.clamp((now - lastAt) / 1000.0F, 0.0F, 0.1F);
		float speed = Float.isNaN(lastMouse) || mouseX < 0 || dt <= 0.0F ? 0.0F : (mouseX - lastMouse) / dt;
		lastMouse = mouseX;
		lastAt = now;
		if (dt <= 0.0F) {
			return;
		}
		float target = Mth.clamp(-speed * STREAM, -SWING_MAX, SWING_MAX);
		SWING[0] += (target - SWING[0]) * (1.0F - (float)Math.exp(-ROOT_FOLLOW * dt));
		for (int i = 1; i < SWING.length; i++) {
			SWING[i] += (SWING[i - 1] - SWING[i]) * (1.0F - (float)Math.exp(-LINK_FOLLOW * dt));
		}
	}

	private static void box(GuiGraphicsExtractor graphics, float x, float y, float w, float h, int color) {
		graphics.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), color);
	}
}
