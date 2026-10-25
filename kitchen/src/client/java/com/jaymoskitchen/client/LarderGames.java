package com.jaymoskitchen.client;

import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Minigames;
import com.jaymoskitchen.net.Payloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The client side of the plain larder's minigames (see {@link Minigames}): chopping, roasting on a spit and stirring
 * the pot. Each one simulates and draws itself; when it's over it reports to the server, which judges.
 */
abstract class LarderGames {
	static final int COLOR_TEXT = 0xFFFFFFFF;
	static final int COLOR_DIM = KitchenUi.DIM;
	static final int COLOR_GOOD = 0xFF55FF55;
	static final int COLOR_WARN = 0xFFFFDD55;
	static final int COLOR_BAD = 0xFFFF5555;

	final Dish dish;
	final Random random = new Random();
	/** Ticks since the game began. */
	int ticks;
	/** Set once the report has gone to the server. */
	boolean over;

	LarderGames(Dish dish) {
		this.dish = dish;
	}

	static LarderGames of(Dish dish) {
		return switch (dish.method()) {
			case CHOP -> new Chop(dish);
			case ROAST -> new Roast(dish);
			default -> new Stir(dish);
		};
	}

	String hintKey() {
		return "screen.jaymoskitchen." + dish.method().name().toLowerCase() + "_hint";
	}

	void tick(double mouseX, double mouseY) {
		ticks++;
	}

	/** SPACE or a click. */
	void press() {
	}

	/** The panel's second button (take the roast off). */
	void serve() {
	}

	final void report(int a, int b, int c, int d) {
		if (!over) {
			over = true;
			ClientPlayNetworking.send(new Payloads.MinigameFinish(a, b, c, d));
		}
	}

	/** Draws the game inside the panel's play area: left to right, from {@code top} down to {@code bottom}. */
	abstract void draw(GuiGraphicsExtractor graphics, Font font, int left, int right, int top, int bottom, float a);

	static void disc(GuiGraphicsExtractor graphics, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int half = (int)Math.sqrt(r * r - dy * dy);
			graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
		}
	}

	static int lerp(int from, int to, float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		int a = Math.round(Mth.lerp(t, from >>> 24, to >>> 24));
		int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
		int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
		return a << 24 | r << 16 | g << 8 | b;
	}

	/** Chopping: the vegetables slide left under the knife; cut on the marks. */
	static final class Chop extends LarderGames {
		/** Screen pixels per vegetable length. */
		private static final float PX_PER_LENGTH = 520.0F;
		private static final int CHOP_ANIM = 6;
		/** How far the vegetables have slid (vegetable lengths); the knife is at 0. */
		private float slide = -0.25F;
		private float speed = Minigames.CHOP_SPEED;
		private int next;
		/** Per cut: 2 clean, 1 near, 0 ragged; and where each ragged cut actually landed. */
		private final List<Integer> results = new ArrayList<>();
		private final List<Float> cutAt = new ArrayList<>();
		private int chopAnim = -1;
		private int feedback;
		private int lastResult;
		private int doneTicks = -1;

		Chop(Dish dish) {
			super(dish);
		}

		private static float mark(int i) {
			return (i + 0.5F) * Minigames.MARK_GAP;
		}

		@Override
		void tick(double mouseX, double mouseY) {
			super.tick(mouseX, mouseY);
			if (chopAnim >= 0 && ++chopAnim > CHOP_ANIM) {
				chopAnim = -1;
			}
			if (feedback > 0) {
				feedback--;
			}
			if (doneTicks >= 0) {
				if (++doneTicks > 12) {
					finish();
				}
				return;
			}
			slide += speed;
			// a mark that slips past the knife uncut
			if (next < Minigames.CUTS && slide > mark(next) + Minigames.CHOP_GOOD) {
				cut(0, mark(next));
			}
		}

		@Override
		void press() {
			if (doneTicks >= 0 || next >= Minigames.CUTS || chopAnim >= 0) {
				return;
			}
			chopAnim = 0;
			float off = Math.abs(slide - mark(next));
			cut(off <= Minigames.CHOP_PERFECT ? 2 : off <= Minigames.CHOP_GOOD ? 1 : 0, slide);
		}

		private void cut(int result, float at) {
			results.add(result);
			cutAt.add(at);
			lastResult = result;
			feedback = 10;
			next++;
			speed += Minigames.CHOP_SPEED_STEP;
			if (next >= Minigames.CUTS) {
				doneTicks = 0;
			}
		}

		private void finish() {
			int perfects = 0;
			int goods = 0;
			for (int r : results) {
				perfects += r == 2 ? 1 : 0;
				goods += r == 1 ? 1 : 0;
			}
			report(perfects, goods, results.size() - perfects - goods, 0);
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, Font font, int left, int right, int top, int bottom, float a) {
			int cx = (left + right) / 2;
			int boardTop = top + 34;
			// a board just deep enough for the veg and the window under it
			int boardBottom = Math.min(bottom - 8, boardTop + 84);
			// the chopping board
			graphics.fill(left, boardTop, right, boardBottom, 0xFF6B4423);
			graphics.fill(left, boardTop, right, boardTop + 3, 0xFF8B5A2B);
			for (int y = boardTop + 10; y < boardBottom; y += 11) {
				graphics.fill(left, y, right, y + 1, 0xFF5A381C);
			}
			// the vegetables: one long strip, cut pieces left behind as they pass
			int vegTop = boardTop + 26;
			int vegBottom = vegTop + 26;
			int body = switch (dish) {
				case BEETROOT_HASH -> 0xFF8E1A48;
				case FRUIT_SALAD -> 0xFFF2A65A;
				case SKY_FRUIT_SALAD -> 0xFFF0A030;
				case PLOUGHMANS_LUNCH -> 0xFFC8865A;
				default -> 0xFFE58A24;
			};
			int shade = switch (dish) {
				case BEETROOT_HASH -> 0xFF64102F;
				case FRUIT_SALAD -> 0xFFC8703A;
				case SKY_FRUIT_SALAD -> 0xFFC07018;
				case PLOUGHMANS_LUNCH -> 0xFF8A5634;
				default -> 0xFFB8641A;
			};
			float s = doneTicks >= 0 ? slide : slide + speed * a;
			int startX = Math.round(cx + (0.0F - s) * PX_PER_LENGTH);
			int endX = Math.round(cx + (1.0F - s) * PX_PER_LENGTH);
			int x0 = Math.max(left, startX);
			int x1 = Math.min(right, endX);
			if (x1 > x0) {
				graphics.fill(x0, vegTop, x1, vegBottom, body);
				graphics.fill(x0, vegBottom - 6, x1, vegBottom, shade);
				graphics.fill(x0, vegTop, x1, vegTop + 3, lerp(body, 0xFFFFFFFF, 0.25F));
			}
			// the leafy top at the start of the run
			if (startX - 10 > left && startX < right) {
				graphics.fill(startX - 10, vegTop + 4, startX, vegTop + 10, 0xFF4C8A2C);
				graphics.fill(startX - 14, vegTop + 12, startX, vegTop + 18, 0xFF3C7022);
			}
			// marks still to cut, and the cuts already made (a gap where the knife went through)
			for (int i = 0; i < Minigames.CUTS; i++) {
				if (i < cutAt.size()) {
					int x = Math.round(cx + (cutAt.get(i) - s) * PX_PER_LENGTH);
					if (x > left && x < right) {
						graphics.fill(x - 1, vegTop - 2, x + 1, vegBottom + 2, 0xFF3A2410);
					}
				} else {
					int x = Math.round(cx + (mark(i) - s) * PX_PER_LENGTH);
					if (x > left && x < right) {
						for (int y = vegTop - 4; y < vegBottom + 4; y += 4) {
							graphics.fill(x, y, x + 1, y + 2, i == next ? 0xFFFFFFFF : 0xC0FFFFFF);
						}
					}
				}
			}
			// the knife: a blade over the board, dropping as it chops
			float drop = chopAnim < 0 ? 0.0F : Mth.sin((chopAnim + a) / CHOP_ANIM * Mth.PI);
			int bladeBottom = vegTop - 2 + Math.round(drop * 30);
			graphics.fill(cx - 2, bladeBottom - 36, cx + 3, bladeBottom, 0xFFD8D8E0);
			graphics.fill(cx + 3, bladeBottom - 36, cx + 6, bladeBottom - 4, 0xFFA8A8B4);
			graphics.fill(cx - 3, bladeBottom - 52, cx + 7, bladeBottom - 36, 0xFF3A2410);
			// the window the cut has to land in
			int good = Math.round(Minigames.CHOP_GOOD * PX_PER_LENGTH);
			int perfect = Math.round(Minigames.CHOP_PERFECT * PX_PER_LENGTH);
			graphics.fill(cx - good, vegBottom + 6, cx + good + 1, vegBottom + 9, 0xFFE0A030);
			graphics.fill(cx - perfect, vegBottom + 6, cx + perfect + 1, vegBottom + 9, 0xFFFFF0B4);
			// the count, a pip per cut, and how the last one went
			graphics.centeredText(font, Component.translatable("screen.jaymoskitchen.cuts", Math.min(next + 1, Minigames.CUTS), Minigames.CUTS),
				cx, top + 4, COLOR_TEXT);
			for (int i = 0; i < Minigames.CUTS; i++) {
				int px = cx - Minigames.CUTS * 7 + i * 14;
				int color = i >= results.size() ? 0xFF404040 : results.get(i) == 2 ? COLOR_GOOD : results.get(i) == 1 ? COLOR_WARN : COLOR_BAD;
				graphics.fill(px, top + 16, px + 10, top + 24, color);
			}
			if (feedback > 0 && !results.isEmpty()) {
				String key = "screen.jaymoskitchen.chop." + (lastResult == 2 ? "clean" : lastResult == 1 ? "near" : "ragged");
				graphics.centeredText(font, Component.translatable(key), cx, boardTop + 6,
					lastResult == 2 ? COLOR_GOOD : lastResult == 1 ? COLOR_WARN : COLOR_BAD);
			}
		}
	}

	/** Roasting: four sides on a spit; the one facing the fire cooks (its neighbours a little). */
	static final class Roast extends LarderGames {
		private static final float SIDE_SPILL = Minigames.ROAST_SIDE_SPILL;
		private final float[] done = new float[Minigames.SIDES];
		/** The side facing down into the fire. */
		private int down;
		private float heat = 1.0F;
		private int heatTimer = 40;
		private int turnAnim = -1;

		Roast(Dish dish) {
			super(dish);
		}

		@Override
		void tick(double mouseX, double mouseY) {
			super.tick(mouseX, mouseY);
			if (over) {
				return;
			}
			if (--heatTimer <= 0) {
				heat = Mth.lerp(random.nextFloat(), Minigames.ROAST_HEAT_MIN, Minigames.ROAST_HEAT_MAX);
				heatTimer = Minigames.HEAT_GAP_MIN + random.nextInt(Minigames.HEAT_GAP_MAX - Minigames.HEAT_GAP_MIN);
			}
			float perTick = heat / dish.grillTicks();
			done[down] += perTick;
			done[(down + 1) % 4] += perTick * SIDE_SPILL;
			done[(down + 3) % 4] += perTick * SIDE_SPILL;
			if (turnAnim >= 0 && ++turnAnim >= Minigames.TURN_TICKS) {
				turnAnim = -1;
				down = (down + 1) % 4;
			}
			for (float side : done) {
				if (side >= Minigames.ROAST_END) {
					serve();
					return;
				}
			}
		}

		@Override
		void press() {
			if (!over && turnAnim < 0) {
				turnAnim = 0;
			}
		}

		@Override
		void serve() {
			report(Math.round(done[0] * 1000), Math.round(done[1] * 1000), Math.round(done[2] * 1000), Math.round(done[3] * 1000));
		}

		/** Raw pink, then golden, brown and black. */
		private static int shade(float doneness) {
			if (doneness < Minigames.GOLDEN) {
				return lerp(0xFFE8A6A0, 0xFFD9A040, doneness / Minigames.GOLDEN);
			}
			if (doneness < Minigames.BURNT_AT) {
				return lerp(0xFFD9A040, 0xFFB0702C, (doneness - Minigames.GOLDEN) / (Minigames.BURNT_AT - Minigames.GOLDEN));
			}
			return lerp(0xFF8A5025, 0xFF1E140E, (doneness - Minigames.BURNT_AT) / (Minigames.ROAST_END - Minigames.BURNT_AT));
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, Font font, int left, int right, int top, int bottom, float a) {
			int cx = (left + right) / 2;
			int fireY = bottom - 10;
			// the fire pit: stones, embers and flames whose height follows the heat
			graphics.fill(cx - 90, fireY, cx + 90, fireY + 8, 0xFF4A4A4A);
			graphics.fill(cx - 80, fireY - 4, cx + 80, fireY, 0xFFB03A10);
			float time = ticks + a;
			for (int i = 0, x = cx - 76; x < cx + 76; x += 6, i++) {
				float wave = Mth.sin(time * 0.45F + i * 1.7F) * 0.5F + 0.5F;
				int h = Math.round((10 + wave * 12) * heat * heat);
				graphics.fill(x, fireY - 4 - h, x + 6, fireY - 4, 0xFFC8321E);
				graphics.fill(x + 1, fireY - 4 - h * 2 / 3, x + 5, fireY - 4, 0xFFF08228);
				graphics.fill(x + 2, fireY - 4 - h / 3, x + 4, fireY - 4, 0xFFFFD25A);
			}
			// the spit and its posts
			int spitY = fireY - 58;
			graphics.fill(cx - 110, spitY - 1, cx + 110, spitY + 2, 0xFF8A8A8A);
			graphics.fill(cx - 112, spitY, cx - 108, fireY + 8, 0xFF5A3A1C);
			graphics.fill(cx + 108, spitY, cx + 112, fireY + 8, 0xFF5A3A1C);
			// the crank: it turns a quarter while the spit turns
			float turn = turnAnim < 0 ? 0.0F : (turnAnim + a) / Minigames.TURN_TICKS;
			float angle = (down + turn) * Mth.HALF_PI;
			int hx = cx + 112 + Math.round(Mth.cos(angle) * 10);
			int hy = spitY + Math.round(Mth.sin(angle) * 10);
			graphics.fill(Math.min(cx + 112, hx), Math.min(spitY, hy), Math.max(cx + 112, hx) + 2, Math.max(spitY, hy) + 2, 0xFF5A3A1C);
			// the roast seen end-on: each edge shows a side, the bottom edge the one in the fire
			int half = 30;
			int shake = turnAnim < 0 ? 0 : Math.round(Mth.sin(turn * Mth.PI) * 4);
			int rx0 = cx - half - shake;
			int ry0 = spitY - half;
			int rx1 = cx + half - shake;
			int ry1 = spitY + half;
			float avg = (done[0] + done[1] + done[2] + done[3]) / 4.0F;
			graphics.fill(rx0 + 4, ry0 + 4, rx1 - 4, ry1 - 4, shade(avg * 0.9F));
			int band = 8;
			graphics.fill(rx0 + 4, ry1 - band, rx1 - 4, ry1, shade(done[down]));
			graphics.fill(rx1 - band, ry0 + 4, rx1, ry1 - 4, shade(done[(down + 1) % 4]));
			graphics.fill(rx0 + 4, ry0, rx1 - 4, ry0 + band, shade(done[(down + 2) % 4]));
			graphics.fill(rx0, ry0 + 4, rx0 + band, ry1 - 4, shade(done[(down + 3) % 4]));
			graphics.fill(cx - 3 - shake, spitY - 1, cx + 3 - shake, spitY + 2, 0xFF8A8A8A);
			// smoke from a side past golden
			for (int i = 0; i < 4; i++) {
				if (done[i] > Minigames.BURNT_AT * 0.95F) {
					int puff = (int)(time * 1.5F + i * 17) % 40;
					graphics.fill(cx - 10 + i * 6, ry0 - puff, cx - 4 + i * 6, ry0 - puff + 6, done[i] > Minigames.BURNT_AT ? 0x90303030 : 0x60C0C0C0);
				}
			}
			// a meter per side: the golden band marked, the side in the fire flagged
			int mw = 54;
			int mx = cx - (mw + 6) * 2 + 3;
			for (int i = 0; i < 4; i++) {
				int x = mx + i * (mw + 6);
				int y = top + 4;
				graphics.fill(x, y, x + mw, y + 6, 0xFF202020);
				int g0 = x + Math.round(mw * Minigames.GOLDEN / Minigames.ROAST_END);
				int g1 = x + Math.round(mw * Minigames.BURNT_AT / Minigames.ROAST_END);
				graphics.fill(g0, y, g1, y + 6, 0x8055FF55);
				graphics.fill(x, y + 1, x + Math.round(mw * Math.min(1.0F, done[i] / Minigames.ROAST_END)), y + 5, shade(done[i]));
				if (i == down) {
					graphics.fill(x, y + 8, x + mw, y + 9, 0xFFFF8A30);
				}
			}
		}
	}

	/** Stirring: circle the mouse round the pot; the needle shows how fast. */
	static final class Stir extends LarderGames {
		private static final float GAUGE_MAX = 0.7F;
		private int potX;
		private int potY;
		private boolean placed;
		private float lastAngle = Float.NaN;
		/** Stirring speed, radians a tick, smoothed. */
		private float omega;
		private int stirred;
		private int stuck;
		private int spilled;
		private int boilTicks;
		private int nextBoil;
		private float spoonAngle;

		Stir(Dish dish) {
			super(dish);
			nextBoil = Minigames.BOIL_GAP_MIN + random.nextInt(Minigames.BOIL_GAP_MAX - Minigames.BOIL_GAP_MIN);
		}

		private boolean boiling() {
			return boilTicks > 0;
		}

		@Override
		void tick(double mouseX, double mouseY) {
			super.tick(mouseX, mouseY);
			if (over || !placed) {
				return;
			}
			double dx = mouseX - potX;
			double dy = mouseY - potY;
			float step = 0.0F;
			if (dx * dx + dy * dy >= 36) {
				float angle = (float)Math.atan2(dy, dx);
				if (!Float.isNaN(lastAngle)) {
					step = Math.abs(Mth.wrapDegrees((angle - lastAngle) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD;
				}
				lastAngle = angle;
				spoonAngle = angle;
			} else {
				lastAngle = Float.NaN;
			}
			omega = omega * 0.7F + step * 0.3F;
			if (boilTicks > 0) {
				boilTicks--;
			} else if (--nextBoil <= 0) {
				boilTicks = Minigames.BOIL_TICKS;
				nextBoil = Minigames.BOIL_GAP_MIN + random.nextInt(Minigames.BOIL_GAP_MAX - Minigames.BOIL_GAP_MIN);
			}
			float min = boiling() ? Minigames.BOIL_MIN : Minigames.STIR_MIN;
			float max = boiling() ? Minigames.BOIL_MAX : Minigames.STIR_MAX;
			if (omega >= min && omega <= max) {
				stirred++;
			} else if (omega < min) {
				// a moment's grace to get going
				if (ticks > 20) {
					stuck++;
				}
			} else {
				spilled++;
			}
			if (stirred >= Minigames.STIR_TICKS) {
				report(stuck, spilled, 1, 0);
			} else if (stuck >= Minigames.STUCK_CHARRED || ticks >= Minigames.STIR_LIMIT) {
				report(stuck, spilled, 0, 0);
			}
		}

		private int broth() {
			return switch (dish) {
				case BORSCHT -> 0xFF9E1C4A;
				case CARROT_SOUP -> 0xFFE4822A;
				case CHICKEN_SOUP -> 0xFFE2C878;
				case RATATOUILLE -> 0xFFB8402A;
				case CHILI_CON_CARNE -> 0xFF8A2A16;
				case VENISON_STEW -> 0xFF6A3418;
				case FISH_CHOWDER, SEAFOOD_BOIL -> 0xFFDCCCAA;
				case DEEP_SEA_STEW -> 0xFF3A4A5A;
				case PEANUT_BRITTLE -> 0xFFC88A2A;
				case TURNIP_MASH -> 0xFFE8DCC0;
				case PRISMALLARD_STEW -> 0xFFC8A060;
				default -> 0xFF8A5A30;
			};
		}

		@Override
		void draw(GuiGraphicsExtractor graphics, Font font, int left, int right, int top, int bottom, float a) {
			int cx = (left + right) / 2;
			potX = cx - 40;
			potY = (top + bottom) / 2 + 8;
			placed = true;
			float time = ticks + a;
			float min = boiling() ? Minigames.BOIL_MIN : Minigames.STIR_MIN;
			float max = boiling() ? Minigames.BOIL_MAX : Minigames.STIR_MAX;
			// the pot from above: iron rim, broth, bubbles (more when it boils up), a swirl that follows the spoon
			disc(graphics, potX, potY, 58, 0xFF2A2A2E);
			disc(graphics, potX, potY, 52, 0xFF3C3C42);
			int broth = broth();
			disc(graphics, potX, potY, 48, stuck > 40 ? lerp(broth, 0xFF3A2010, (stuck - 40) / 80.0F) : broth);
			int bubbles = boiling() ? 14 : 5;
			for (int i = 0; i < bubbles; i++) {
				float t = (time * 0.07F + i * 0.61F) % 1.0F;
				float ang = i * 2.39F + time * 0.02F;
				float rad = 8 + (i * 13 % 36);
				int bx = potX + Math.round(Mth.cos(ang) * rad);
				int by = potY + Math.round(Mth.sin(ang) * rad);
				int size = 1 + Math.round(t * 3);
				graphics.fill(bx - size, by - size, bx + size, by + size, lerp(broth, 0xFFFFFFFF, 0.35F));
			}
			// the spoon: from the middle out toward the mouse
			for (int r = 0; r < 44; r += 2) {
				int sx = potX + Math.round(Mth.cos(spoonAngle) * r);
				int sy = potY + Math.round(Mth.sin(spoonAngle) * r);
				graphics.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xFF8B5A2B);
			}
			// slops over the rim when stirred too hard
			if (omega > max) {
				for (int i = 0; i < 4; i++) {
					float ang = spoonAngle + i * 0.4F;
					int sx = potX + Math.round(Mth.cos(ang) * 60);
					int sy = potY + Math.round(Mth.sin(ang) * 60);
					graphics.fill(sx - 2, sy - 2, sx + 3, sy + 3, broth);
				}
			}

			// the gauge on the right: the needle against the green band
			int gx = cx + 40;
			// level with the pot, so the labels sit under it clear of the time bar
			int gTop = potY - 58;
			int gBottom = Math.min(bottom - 20, potY + 58);
			int gh = gBottom - gTop;
			graphics.fill(gx - 1, gTop - 1, gx + 15, gBottom + 1, 0xFF505050);
			graphics.fill(gx, gTop, gx + 14, gBottom, 0xFF202020);
			int b0 = gBottom - Math.round(gh * max / GAUGE_MAX);
			int b1 = gBottom - Math.round(gh * min / GAUGE_MAX);
			graphics.fill(gx, b0, gx + 14, b1, boiling() ? 0xC0E0A030 : 0xC055C055);
			int ny = gBottom - Math.round(gh * Math.min(1.0F, omega / GAUGE_MAX));
			graphics.fill(gx - 4, ny - 1, gx + 18, ny + 1, COLOR_TEXT);
			// the stirred-through bar, and what's going wrong
			int px = gx + 26;
			graphics.fill(px, gTop, px + 8, gBottom, 0xFF202020);
			int fillTop = gBottom - Math.round(gh * Math.min(1.0F, stirred / (float)Minigames.STIR_TICKS));
			graphics.fill(px, fillTop, px + 8, gBottom, COLOR_GOOD);
			graphics.centeredText(font, Component.translatable("screen.jaymoskitchen.stirred"), px + 4, gBottom + 4, COLOR_DIM);
			Component status = boiling() ? Component.translatable("screen.jaymoskitchen.stir_boil")
				: omega < min && ticks > 20 ? Component.translatable("screen.jaymoskitchen.stir_slow")
				: omega > max ? Component.translatable("screen.jaymoskitchen.stir_fast") : null;
			if (status != null) {
				graphics.centeredText(font, status, cx, top + 4, boiling() ? COLOR_WARN : COLOR_BAD);
			}
			// the time left
			float left_ = 1.0F - ticks / (float)Minigames.STIR_LIMIT;
			graphics.fill(left, bottom - 4, right, bottom - 1, 0xFF202020);
			graphics.fill(left, bottom - 4, left + Math.round((right - left) * left_), bottom - 1, 0xFFE0A030);
		}
	}
}
