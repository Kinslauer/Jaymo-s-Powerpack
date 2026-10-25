package com.jaymoskitchen.client;

import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Grill;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Draws the campfire grill: flickering flames, a grate, and the food as a big pixel sprite whose colour follows
 * how cooked its top side is. The hidden bottom side shows as a thin edge strip and as smoke.
 */
final class GrillView {
	/** Screen pixels per sprite pixel. */
	static final int SCALE = 6;
	static final int SPRITE = 16 * SCALE;

	private static final int OUTLINE = 0xFF28180C;
	private static final int BURNT = 0xFF2D1C12;
	private static final int ASH = 0xFF141010;

	/** One sprite colour: raw and cooked-through; burning darkens from cooked. */
	private record Shade(int raw, int cooked) {
	}

	private static final Map<Character, Shade> SHADES = new HashMap<>();

	static {
		// salmon fillet
		SHADES.put('p', new Shade(0xFFFA8072, 0xFFF0AA82));
		SHADES.put('P', new Shade(0xFFD7645A, 0xFFCD8C64));
		SHADES.put('q', new Shade(0xFFFFBEAA, 0xFFFFD7BE));
		// grill marks appear as it cooks
		SHADES.put('x', new Shade(0xFFFA8072, 0xFF6E3C1E));
		// chicken: pale pink skin to golden honey glaze
		SHADES.put('a', new Shade(0xFFF0BEAA, 0xFFD68C28));
		SHADES.put('A', new Shade(0xFFD7A08C, 0xFFAA6018));
		SHADES.put('u', new Shade(0xFFFFD7C8, 0xFFFFC85A));
		SHADES.put('y', new Shade(0xFFF0BEAA, 0xFF7A3E12));
		// mutton kebab: meat, roasted carrot, the skewer
		SHADES.put('m', new Shade(0xFFC8464B, 0xFF8C5032));
		SHADES.put('M', new Shade(0xFFA0323A, 0xFF6E3C24));
		SHADES.put('o', new Shade(0xFFEB8C28, 0xFFC8641E));
		SHADES.put('w', new Shade(0xFFB48C50, 0xFF9A6E3A));
		// pork chop: pink to golden, fat crisping, berry glaze darkening
		SHADES.put('r', new Shade(0xFFF0A0A0, 0xFFC88250));
		SHADES.put('R', new Shade(0xFFD28282, 0xFFA0643A));
		SHADES.put('f', new Shade(0xFFFFF0E6, 0xFFE6C38C));
		SHADES.put('g', new Shade(0xFFC8283C, 0xFF8C1428));
		// bone barely changes
		SHADES.put('i', new Shade(0xFFF0ECDC, 0xFFE6DEC8));
		SHADES.put('I', new Shade(0xFFC8C2B0, 0xFFBEB49C));
	}

	private static final String[] SALMON = {
		"................",
		"................",
		"................",
		"................",
		"......kkkkkk....",
		"....kkqqpppPkk..",
		"...kqqpxppxppPk.",
		"..kqpxppxppxpPk.",
		"..kppppxppxpPPk.",
		"..kPpxppxppPPk..",
		"...kkPPPPPPkk...",
		"................",
		"................",
		"................",
		"................",
		"................",
	};

	private static final String[] CHICKEN = {
		"................",
		"................",
		"................",
		".....kkkk.......",
		"....kuuaak......",
		"...kuaaaaAk.....",
		"...kaayaaaAk....",
		"...kaaayaaAk....",
		"....kAaaaAAk....",
		".....kAAAAkk....",
		"......kkkIik....",
		"........kIik....",
		".......kiiIik...",
		".......kIkkIk...",
		"........k..k....",
		"................",
	};

	private static final String[] KEBAB = {
		"................",
		"................",
		"................",
		"................",
		"..kkkk.kkk.kkkk.",
		".kmmMkkookkmmMk.",
		"wkmmMkkookkmmMkw",
		"wwmmMwwoowwmmMww",
		".kmMMkkookkmMMk.",
		"..kkkk.kkk.kkkk.",
		"................",
		"................",
		"................",
		"................",
		"................",
		"................",
	};

	private static final String[] PORK_CHOP = {
		"................",
		"................",
		"................",
		"....kkkkkk......",
		"...kfrrrrRk.....",
		"..kfrrgrrrRk....",
		"..kfrrrrgrRRk...",
		"..kfrgrrrrRRk...",
		"...kfrrrgRRk....",
		"....kfrrRRkkkk..",
		".....kkRRRkiiik.",
		"......kkkkkkkk..",
		"................",
		"................",
		"................",
		"................",
	};

	private GrillView() {
	}

	private static String[] sprite(Dish dish) {
		return switch (dish) {
			case HONEY_GLAZED_CHICKEN, HONEY_DRUMSTICKS -> CHICKEN;
			case MUTTON_KEBAB, CRIMSON_SKEWER, BUSHMEAT_SKEWER -> KEBAB;
			case GLAZED_PORK_CHOP, ORANGE_GLAZED_TAEGORE -> PORK_CHOP;
			default -> SALMON;
		};
	}

	/**
	 * @param top    doneness of the side facing up
	 * @param bottom doneness of the side on the fire
	 * @param flip   0..1 progress through a flip animation, or -1 when not flipping
	 * @param heat   the fire's strength (1.0 = normal): taller flames cook faster
	 * @param soul   a soul campfire: blue flames
	 * @param time   animation clock in ticks (with partial tick)
	 */
	static void draw(GuiGraphicsExtractor graphics, Dish dish, int cx, int baseY, float top, float bottom, float flip,
		float heat, boolean soul, float time) {
		drawFire(graphics, cx, baseY, heat, soul, time);

		// grate
		int grateY = baseY - 34;
		graphics.fill(cx - 70, grateY, cx + 70, grateY + 3, 0xFF3A3A3A);
		for (int bx = cx - 66; bx < cx + 70; bx += 12) {
			graphics.fill(bx, grateY - 1, bx + 2, grateY + 4, 0xFF555555);
		}

		drawSmoke(graphics, cx, grateY - SPRITE, bottom, time);

		// the food: during a flip it is tossed up and squashed through edge-on
		float squash = 1.0F;
		float lift = 0.0F;
		if (flip >= 0.0F) {
			squash = Math.abs(Mth.cos(flip * Mth.PI));
			lift = Mth.sin(flip * Mth.PI) * 28.0F;
		}
		String[] rows = sprite(dish);
		int firstRow = 16;
		int lastRow = -1;
		for (int y = 0; y < 16; y++) {
			if (!rows[y].isBlank()) {
				firstRow = Math.min(firstRow, y);
				lastRow = y;
			}
		}
		// sit the sprite's lowest pixel row on the grate
		int left = cx - SPRITE / 2;
		float centerY = grateY - (lastRow - firstRow + 1) * SCALE / 2.0F - lift;
		float rowsMid = (firstRow + lastRow + 1) / 2.0F;
		for (int y = firstRow; y <= lastRow; y++) {
			int y0 = Math.round(centerY + (y - rowsMid) * SCALE * squash);
			int y1 = Math.round(centerY + (y + 1 - rowsMid) * SCALE * squash);
			if (y1 <= y0) {
				y1 = y0 + 1;
			}
			String row = rows[y];
			for (int x = 0; x < 16; x++) {
				char c = row.charAt(x);
				if (c == '.') {
					continue;
				}
				int color = c == 'k' ? OUTLINE : color(SHADES.get(c), top);
				graphics.fill(left + x * SCALE, y0, left + (x + 1) * SCALE, y1, color);
			}
		}
		// underside edge: a glimpse of the side on the fire
		if (flip < 0.0F) {
			int edgeY = Math.round(centerY + (lastRow + 1 - rowsMid) * SCALE) - 2;
			String row = rows[lastRow];
			for (int x = 0; x < 16; x++) {
				char c = row.charAt(x);
				if (c != '.' && c != 'k') {
					graphics.fill(left + x * SCALE, edgeY, left + (x + 1) * SCALE, edgeY + 2, color(SHADES.get(c), bottom));
				}
			}
		}
	}

	private static int color(Shade shade, float doneness) {
		return color(shade.raw, shade.cooked, doneness);
	}

	/** Raw → cooked as doneness reaches golden, then browning, then burning to black. */
	static int color(int raw, int cooked, float doneness) {
		if (doneness <= Grill.GOLDEN) {
			return lerp(raw, cooked, Mth.clamp(doneness / Grill.GOLDEN, 0.0F, 1.0F));
		}
		if (doneness <= Grill.BURNT) {
			// golden to deep brown
			return lerp(cooked, darken(cooked, 0.7F), (doneness - Grill.GOLDEN) / (Grill.BURNT - Grill.GOLDEN));
		}
		float t = Mth.clamp((doneness - Grill.BURNT) / (Grill.ASH - Grill.BURNT), 0.0F, 1.0F);
		return t < 0.5F
			? lerp(darken(cooked, 0.7F), BURNT, t * 2.0F)
			: lerp(BURNT, ASH, (t - 0.5F) * 2.0F);
	}

	/** Outer, middle and inner flame colours: orange fire, or blue soul fire. */
	private static final int[] FIRE = {0xFFC8321E, 0xFFF08228, 0xFFFFD25A};
	private static final int[] SOUL_FIRE = {0xFF1E5AA0, 0xFF32B4D2, 0xFFAAF0FF};

	static void drawFire(GuiGraphicsExtractor graphics, int cx, int baseY, float heat, boolean soul, float time) {
		int[] colors = soul ? SOUL_FIRE : FIRE;
		// logs
		graphics.fill(cx - 60, baseY - 10, cx + 60, baseY - 2, 0xFF5A3A1E);
		graphics.fill(cx - 50, baseY - 14, cx + 46, baseY - 8, 0xFF6E4828);
		// flames: columns rising and falling out of phase
		int col = 0;
		for (int x = cx - 56; x < cx + 56; x += 4, col++) {
			float wave = Mth.sin(time * 0.35F + col * 1.7F) * 0.5F + Mth.sin(time * 0.9F + col * 0.6F) * 0.5F;
			int edgeFade = Math.min(x - (cx - 60), (cx + 60) - x);
			int height = Math.max(4, Math.min(40, Math.round((10 + edgeFade / 3 + wave * 6.0F) * heat)));
			int y = baseY - 12;
			graphics.fill(x, y - height, x + 4, y, colors[0]);
			graphics.fill(x, y - height * 2 / 3, x + 4, y, colors[1]);
			graphics.fill(x, y - height / 3, x + 4, y, colors[2]);
		}
	}

	static void drawSmoke(GuiGraphicsExtractor graphics, int cx, int topY, float bottom, float time) {
		if (bottom < 0.7F) {
			return;
		}
		boolean burning = bottom > Grill.BURNT;
		int puffs = burning ? 7 : 3;
		int alpha = burning ? 0xC0 : 0x60;
		int rgb = burning ? 0x302828 : 0xC8C8C8;
		for (int i = 0; i < puffs; i++) {
			float phase = (time * 0.04F + i / (float) puffs) % 1.0F;
			int px = cx - 30 + (i * 37) % 60 + Math.round(Mth.sin(time * 0.1F + i) * 4.0F);
			int py = topY + 20 - Math.round(phase * 40.0F);
			int size = 6 + Math.round(phase * 8.0F);
			int a = Math.round(alpha * (1.0F - phase));
			graphics.fill(px, py, px + size, py + size, (a << 24) | rgb);
		}
	}

	private static int darken(int argb, float factor) {
		int r = Math.round(((argb >> 16) & 0xFF) * factor);
		int g = Math.round(((argb >> 8) & 0xFF) * factor);
		int b = Math.round((argb & 0xFF) * factor);
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	private static int lerp(int from, int to, float t) {
		int r = Math.round(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
