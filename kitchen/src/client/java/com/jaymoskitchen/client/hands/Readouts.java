package com.jaymoskitchen.client.hands;

import com.jaymoskitchen.client.hud.HudStyle;
import com.jaymoskitchen.cooking.Minigames;
import com.jaymoskitchen.cooking.Prep;
import com.jaymoskitchen.cooking.Rolling;
import com.jaymoskitchen.cooking.hands.Pick;
import com.jaymoskitchen.net.Payloads.HandsSync;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The readout of each running kind: what {@code Run#fill} sends (ints and floats, laid out per kind there), drawn in the
 * plate under the crosshair. {@code age} is the ticks since the server last said, for what moves between its messages.
 */
final class Readouts {
	/** The plate's inside width. */
	static final int W = HandsHud.WIDTH - HandsHud.PAD * 2;
	static final int GOOD = 0xFF55FF55, WARN = 0xFFFFDD55, BAD = 0xFFFF5555, WATER = 0xFF3F76E4, OIL = 0xFFE0B040;

	private Readouts() {
	}

	/** A line of text and the dish's name and how many are at it, for kinds with no drawer of their own yet. */
	private static final HandsHud.Drawer PLAIN = new HandsHud.Drawer() {
		@Override
		public int height(HandsSync sync) {
			return HandsHud.LINE;
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Font font, HandsSync sync, int x, int y, float age) {
			HudStyle.text(graphics, font, HandsHud.dishName(sync.dish(), sync.kind()), x, y, HudStyle.TEXT);
		}
	};

	static HandsHud.Drawer of(int kind) {
		Pick.Kind k = Pick.Kind.byIndex(kind);
		if (k == null) {
			return PLAIN;
		}
		return switch (k) {
			case CHOP -> CHOP;
			case STIR -> STIR;
			case DOUGH -> DOUGH;
			case JOINT -> JOINT;
			default -> PLAIN;
		};
	}

	private static int get(int[] a, int i) {
		return i < a.length ? a[i] : 0;
	}

	private static float get(float[] a, int i) {
		return i < a.length ? a[i] : 0.0F;
	}

	/** A band over a bar: {@code from} to {@code to} (0 to 1) in {@code color}. */
	private static void band(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float from, float to, int color) {
		int x0 = x + Math.round(w * Math.max(0.0F, Math.min(1.0F, from)));
		int x1 = x + Math.round(w * Math.max(0.0F, Math.min(1.0F, to)));
		if (x1 > x0) {
			graphics.fill(x0, y, x1, y + h, color);
		}
	}

	/** The marker on a gauge. */
	private static void needle(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float at) {
		int nx = x + Math.round(w * Math.max(0.0F, Math.min(1.0F, at)));
		graphics.fill(nx - 1, y - 2, nx + 1, y + h + 2, 0xFFFFFFFF);
	}

	// --- chopping ---

	private static final float CHOP_PX = 360.0F;

	private static final HandsHud.Drawer CHOP = new HandsHud.Drawer() {
		@Override
		public int height(HandsSync sync) {
			return 12 + 6 + 8;
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Font font, HandsSync sync, int x, int y, float age) {
			int[] i = sync.ints();
			float[] f = sync.floats();
			int next = get(i, 0);
			float slide = get(f, 0) + get(f, 1) * age;
			int cx = x + W / 2;
			graphics.fill(x, y, x + W, y + 12, 0xFF3A2410);
			int x0 = Math.max(x, Math.round(cx + (0.0F - slide) * CHOP_PX));
			int x1 = Math.min(x + W, Math.round(cx + (1.0F - slide) * CHOP_PX));
			if (x1 > x0) {
				graphics.fill(x0, y + 2, x1, y + 10, 0xFFE58A24);
				graphics.fill(x0, y + 7, x1, y + 10, 0xFFB8641A);
			}
			for (int m = 0; m < Minigames.CUTS; m++) {
				int mx = Math.round(cx + ((m + 0.5F) * Minigames.MARK_GAP - slide) * CHOP_PX);
				if (mx > x && mx < x + W) {
					graphics.fill(mx, y, mx + 1, y + 12, m < next ? 0xFF1A0E06 : 0xFFFFFFFF);
				}
			}
			int good = Math.round(Minigames.CHOP_GOOD * CHOP_PX);
			int perfect = Math.round(Minigames.CHOP_PERFECT * CHOP_PX);
			graphics.fill(cx - good, y + 13, cx + good + 1, y + 15, 0xFFE0A030);
			graphics.fill(cx - perfect, y + 13, cx + perfect + 1, y + 15, 0xFFFFF0B4);
			graphics.fill(cx, y - 2, cx + 1, y + 14, 0xFFD8D8E0);
			for (int p = 0; p < Minigames.CUTS; p++) {
				int px = x + (W - Minigames.CUTS * 14) / 2 + p * 14;
				int color = 0xFF404040;
				if (p < next && 1 + p < i.length) {
					color = i[1 + p] == 2 ? GOOD : i[1 + p] == 1 ? WARN : BAD;
				}
				graphics.fill(px, y + 18, px + 10, y + 24, color);
			}
		}
	};

	// --- stirring ---

	private static final float GAUGE_MAX = 0.7F;

	private static final HandsHud.Drawer STIR = new HandsHud.Drawer() {
		@Override
		public int height(HandsSync sync) {
			return HandsHud.LINE + 2 + 6 + 6;
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Font font, HandsSync sync, int x, int y, float age) {
			int[] i = sync.ints();
			float omega = get(sync.floats(), 0);
			boolean boiling = get(i, 3) == 1;
			float min = boiling ? Minigames.BOIL_MIN : Minigames.STIR_MIN;
			float max = boiling ? Minigames.BOIL_MAX : Minigames.STIR_MAX;
			Component status = boiling ? Component.translatable("screen.jaymoskitchen.stir_boil")
				: omega < min && get(i, 4) > 20 ? Component.translatable("screen.jaymoskitchen.stir_slow")
				: omega > max ? Component.translatable("screen.jaymoskitchen.stir_fast")
				: Component.translatable("hud.jaymoskitchen.hands.stir");
			HudStyle.text(graphics, font, status, x, y, boiling ? WARN : omega < min && get(i, 4) > 20 || omega > max ? BAD : HudStyle.TEXT);
			int gy = y + HandsHud.LINE + 2;
			HudStyle.bar(graphics, x, gy, W, 6, 0.0F, 0xFFFFFF);
			band(graphics, x, gy, W, 6, min / GAUGE_MAX, max / GAUGE_MAX, boiling ? 0xC0E0A030 : 0xC055C055);
			needle(graphics, x, gy, W, 6, omega / GAUGE_MAX);
			HudStyle.bar(graphics, x, gy + 9, W, 3, get(i, 0) / (float) Minigames.STIR_TICKS, GOOD);
		}
	};

	// --- dough ---

	private static final HandsHud.Drawer DOUGH = new HandsHud.Drawer() {
		@Override
		public int height(HandsSync sync) {
			return HandsHud.LINE + 2 + 7 + 3;
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Font font, HandsSync sync, int x, int y, float age) {
			int[] i = sync.ints();
			float[] f = sync.floats();
			int phase = get(i, 0);
			int verdict = get(i, 2);
			Component line = verdict >= 0 ? Component.translatable(verdict == 0 ? "hud.jaymoskitchen.hands.dough.off"
				: verdict == 2 ? "screen.jaymoskitchen.prep.just_right" : "hud.jaymoskitchen.hands.dough.near")
				: Component.translatable("hud.jaymoskitchen.hands.dough." + (phase == 0 ? "water" : phase == 1 ? "oil" : "knead"));
			HudStyle.text(graphics, font, line, x, y, verdict < 0 ? HudStyle.TEXT : verdict == 0 ? BAD : verdict == 2 ? GOOD : WARN);
			int gy = y + HandsHud.LINE + 2;
			if (phase == 2) {
				HudStyle.bar(graphics, x, gy, W, 7, get(i, 1) / (float) Prep.KNEADS, GOOD);
				return;
			}
			boolean water = phase == 0;
			float level = water ? get(f, 0) : get(f, 1);
			float shift = water ? get(f, 2) : get(f, 3);
			HudStyle.bar(graphics, x, gy, W, 7, level, water ? WATER : OIL);
			float min = (water ? Prep.WATER_MIN : Prep.OIL_MIN) + shift, max = (water ? Prep.WATER_MAX : Prep.OIL_MAX) + shift;
			float pmin = (water ? Prep.WATER_PERFECT_MIN : Prep.OIL_PERFECT_MIN) + shift;
			float pmax = (water ? Prep.WATER_PERFECT_MAX : Prep.OIL_PERFECT_MAX) + shift;
			band(graphics, x, gy, W, 7, min, max, 0x8055FF55);
			band(graphics, x, gy, W, 7, pmin, pmax, 0xB0FFFFFF);
		}
	};

	// --- the joint ---

	private static final String[] JOINT_VERDICTS = {"", "screen.jaymoskitchen.roll.ground", "screen.jaymoskitchen.roll.too_slow",
		"screen.jaymoskitchen.roll.uneven", "screen.jaymoskitchen.roll.even", "screen.jaymoskitchen.roll.lumpy", "screen.jaymoskitchen.roll.torn"};

	private static final HandsHud.Drawer JOINT = new HandsHud.Drawer() {
		@Override
		public int height(HandsSync sync) {
			return HandsHud.LINE + 2 + 14;
		}

		@Override
		public void draw(GuiGraphicsExtractor graphics, Font font, HandsSync sync, int x, int y, float age) {
			int[] i = sync.ints();
			float[] f = sync.floats();
			int phase = get(i, 0);
			int verdict = Math.max(0, Math.min(JOINT_VERDICTS.length - 1, get(i, 1)));
			Component line = verdict > 0 ? Component.translatable(JOINT_VERDICTS[verdict])
				: Component.translatable("hud.jaymoskitchen.hands.joint." + (phase == 0 ? "grind" : phase == 1 ? "sprinkle" : "roll"));
			HudStyle.text(graphics, font, line, x, y, verdict == 0 ? HudStyle.TEXT : verdict == 1 || verdict == 4 ? GOOD : verdict == 5 ? WARN : BAD);
			int gy = y + HandsHud.LINE + 2;
			switch (phase) {
				case 0 -> HudStyle.bar(graphics, x, gy + 4, W, 6, get(f, 0) / Math.max(1.0F, get(f, 1)), 0xFF9AA0A8);
				case 1 -> {
					// the stretches of paper, each as high as the herb on it; the level they should all be
					float target = Rolling.SUPPLY / Rolling.SEGMENTS;
					int w = W / Rolling.SEGMENTS;
					for (int s = 0; s < Rolling.SEGMENTS; s++) {
						float amount = get(f, 7 + s);
						int h = Math.round(14 * Math.min(1.0F, amount / (target * 2.0F)));
						graphics.fill(x + s * w + 1, gy + 14 - h, x + s * w + w - 1, gy + 14, 0xFF5E9A3A);
					}
					graphics.fill(x, gy + 7, x + W, gy + 8, 0xA0FFFFFF);
				}
				default -> {
					HudStyle.bar(graphics, x, gy + 1, W, 5, get(f, 4) / Rolling.ROLL_LENGTH, GOOD);
					HudStyle.bar(graphics, x, gy + 9, W, 3, get(f, 5) / Rolling.TEAR_LIMIT, BAD);
				}
			}
		}
	};
}
