package com.jaymospowers.client.power.wind;

import com.jaymospowers.power.wind.WindNet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Timed screen effects, drawn over the HUD for whoever the server sends them to (they needn't have the wind
 * themselves; see {@link WindNet.ScreenFx}):
 * <ul>
 * <li><b>Vignette</b>: a coloured frame that swells in over {@link #VIGNETTE_IN} ticks and fades out over the rest
 * (a Stormbound mode's blue, a Tempest's white).</li>
 * <li><b>Lightning</b>: the whole screen flashes white, drops to a black after-image, flickers white once more and
 * fades out of the dark, over {@link #LIGHTNING_TICKS} ticks (the Thunderhead).</li>
 * </ul>
 * Small and self-contained so any wind effect can use it: {@link #vignette} and {@link #lightning}.
 */
final class WindScreenFx {
	/** A vignette's strongest alpha (0-255), and how many ticks it takes to swell in. */
	private static final int VIGNETTE_ALPHA = 0x78;
	private static final int VIGNETTE_IN = 4;
	/** The frame's depth, as a share of the smaller screen side, and how many soft steps it's drawn in. */
	private static final float VIGNETTE_DEPTH = 0.22F;
	private static final int VIGNETTE_STEPS = 10;
	/** A lightning flash's length in ticks. */
	private static final int LIGHTNING_TICKS = 18;
	/** At most this many effects at once (a storm of packets can't stack up forever). */
	private static final int MAX_ACTIVE = 8;

	private static final class Fx {
		final int kind;
		final int color;
		final int ticks;
		int age;

		Fx(int kind, int color, int ticks) {
			this.kind = kind;
			this.color = color & 0xFFFFFF;
			this.ticks = Math.max(1, ticks);
		}
	}

	private static final List<Fx> ACTIVE = new ArrayList<>();

	private WindScreenFx() {
	}

	/** A packet from the server. */
	static void onPacket(WindNet.ScreenFx payload) {
		if (payload.kind() == WindNet.ScreenFx.LIGHTNING) {
			lightning();
		} else {
			vignette(payload.color(), payload.ticks());
		}
	}

	/** A {@code color} (0xRRGGBB) frame around the screen for {@code ticks}. */
	static void vignette(int color, int ticks) {
		add(new Fx(WindNet.ScreenFx.VIGNETTE, color, ticks));
	}

	/** A lightning flash: white, black, white, and back. */
	static void lightning() {
		add(new Fx(WindNet.ScreenFx.LIGHTNING, 0xFFFFFF, LIGHTNING_TICKS));
	}

	private static void add(Fx fx) {
		if (ACTIVE.size() >= MAX_ACTIVE) {
			ACTIVE.remove(0);
		}
		ACTIVE.add(fx);
	}

	static void tick() {
		for (Iterator<Fx> it = ACTIVE.iterator(); it.hasNext(); ) {
			Fx fx = it.next();
			if (++fx.age >= fx.ticks) {
				it.remove();
			}
		}
	}

	static void clear() {
		ACTIVE.clear();
	}

	/** The HUD element (registered after the vanilla overlays). */
	static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		for (Fx fx : ACTIVE) {
			float age = Math.min(fx.ticks, fx.age + partial);
			if (fx.kind == WindNet.ScreenFx.LIGHTNING) {
				lightning(graphics, width, height, age);
			} else {
				vignette(graphics, width, height, fx, age);
			}
		}
	}

	private static void vignette(GuiGraphicsExtractor graphics, int width, int height, Fx fx, float age) {
		float in = Mth.clamp(age / VIGNETTE_IN, 0.0F, 1.0F);
		float out = 1.0F - Mth.clamp((age - VIGNETTE_IN) / Math.max(1.0F, fx.ticks - VIGNETTE_IN), 0.0F, 1.0F);
		int alpha = (int)(VIGNETTE_ALPHA * in * out * out);
		if (alpha <= 0) {
			return;
		}
		int depth = Math.max(8, (int)(Math.min(width, height) * VIGNETTE_DEPTH));
		int step = Math.max(1, depth / VIGNETTE_STEPS);
		// nested frames, densest at the edge: each step inward is fainter
		for (int i = 0; i < VIGNETTE_STEPS; i++) {
			int a = alpha * (VIGNETTE_STEPS - i) * (VIGNETTE_STEPS - i) / (VIGNETTE_STEPS * VIGNETTE_STEPS);
			if (a <= 0) {
				break;
			}
			int c = a << 24 | fx.color;
			int o = i * step;
			graphics.fill(o, o, width - o, o + step, c);
			graphics.fill(o, height - o - step, width - o, height - o, c);
			graphics.fill(o, o + step, o + step, height - o - step, c);
			graphics.fill(width - o - step, o + step, width - o, height - o - step, c);
		}
	}

	/** White (0-2), black (2-5), white again (5-6), then the dark lifts (6-end). */
	private static void lightning(GuiGraphicsExtractor graphics, int width, int height, float age) {
		int color;
		float alpha;
		if (age < 2.0F) {
			color = 0xFFFFFF;
			alpha = 0.85F - 0.1F * age;
		} else if (age < 5.0F) {
			color = 0x000000;
			alpha = 0.7F;
		} else if (age < 6.0F) {
			color = 0xEEF4FF;
			alpha = 0.5F;
		} else {
			color = 0x000000;
			alpha = 0.6F * (1.0F - Mth.clamp((age - 6.0F) / (LIGHTNING_TICKS - 6.0F), 0.0F, 1.0F));
		}
		int a = (int)(255 * alpha);
		if (a > 0) {
			graphics.fill(0, 0, width, height, a << 24 | color);
		}
	}
}
