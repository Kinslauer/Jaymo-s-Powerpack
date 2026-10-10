package com.jaymoskitchen.client;

import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Sear;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Draws the blast furnace searing minigame: the heat bar with the target (and its white-hot core) and the
 * sweeping marker, a pip per round, and the meat in a skillet over a white-hot blast. Every hit leaves a sear
 * mark across the meat; a miss leaves a charred blotch.
 */
final class SearView {
	private static final int SCALE = 6;
	private static final int OUTLINE = 0xFF28180C;

	private static final String[] CUT = {
		"....kkkkkkk.....",
		"..kkmmmmmmmkk...",
		".kmmmmfmmmmmmk..",
		".kmmmmmmmmmmmmk.",
		"kmmmmmmmmmmMMMk.",
		"kmmmfmmmmmMMMk..",
		".kmmmmmmmMMMk...",
		"..kkMMMMMMkk....",
		"....kkkkkk......",
	};

	private SearView() {
	}

	/**
	 * @param results  per finished round: 2 perfect, 1 good, 0 miss
	 * @param feedback ticks left showing the last result (0 while a round is live)
	 */
	static void draw(GuiGraphicsExtractor graphics, Font font, Dish dish, int cx, int top, int barLeft, int barRight,
		int baseY, float marker, float zone, int round, List<Integer> results, int feedback, float time) {
		int barW = barRight - barLeft;

		// heat bar: target zone, white-hot core, and the marker
		graphics.fill(barLeft - 2, top - 2, barRight + 2, top + 14, 0xFF3C3C3C);
		graphics.fill(barLeft, top, barRight, top + 12, 0xFF5A1A10);
		int zl = barLeft + Math.round(barW * (zone - Sear.ZONE_WIDTH / 2));
		int zr = barLeft + Math.round(barW * (zone + Sear.ZONE_WIDTH / 2));
		graphics.fill(zl, top, zr, top + 12, 0xFFE08A28);
		int cl = barLeft + Math.round(barW * (zone - Sear.CORE_WIDTH / 2));
		int cr = barLeft + Math.round(barW * (zone + Sear.CORE_WIDTH / 2));
		graphics.fill(cl, top, cr, top + 12, 0xFFFFF0B4);
		if (feedback == 0) {
			int mx = barLeft + Math.round(barW * marker);
			graphics.fill(mx - 1, top - 4, mx + 2, top + 16, 0xFFFFFFFF);
		}

		// a pip per round
		int pipY = top + 20;
		for (int i = 0; i < Sear.ROUNDS; i++) {
			int px = cx - Sear.ROUNDS * 9 + i * 18;
			int color = i < results.size()
				? switch (results.get(i)) {
					case 2 -> 0xFF55FF55;
					case 1 -> 0xFFFFDD55;
					default -> 0xFFFF5555;
				}
				: i == round ? 0xFFFFFFFF : 0xFF505050;
			graphics.fill(px, pipY, px + 12, pipY + 6, color);
		}

		// the blast: an iron chamber roaring white-hot at the bottom
		int boxTop = top + 34;
		graphics.fill(cx - 112, boxTop - 2, cx + 112, baseY + 2, 0xFF2E2E34);
		graphics.fill(cx - 110, boxTop, cx + 110, baseY, 0xFF141418);
		graphics.fillGradient(cx - 110, boxTop + 30, cx + 110, baseY, 0x00FF8C28, 0xE0FFB450);
		for (int i = 0; i < 14; i++) {
			// sparks shooting up
			float phase = (time * 0.05F + i * 0.137F) % 1.0F;
			int sx = cx - 100 + Math.floorMod(i * 61 + Math.round(Mth.sin(time * 0.2F + i) * 5.0F), 200);
			int sy = baseY - 6 - Math.round(phase * (baseY - boxTop - 12));
			graphics.fill(sx, sy, sx + 2, sy + 2, ((int) (255 * (1.0F - phase)) << 24) | 0xFFD25A);
		}

		// skillet with the meat
		int panY = baseY - 30;
		graphics.fill(cx - 66, panY, cx + 66, panY + 8, 0xFF2A2A2A);
		graphics.fill(cx + 66, panY + 2, cx + 104, panY + 6, 0xFF3A3A3A);
		boolean rabbit = dish == Dish.MAGMA_RABBIT;
		int meat = rabbit ? 0xFFB4785A : 0xFF9A4632;
		int meatDark = rabbit ? 0xFF8C5A40 : 0xFF6E2E1E;
		int fat = rabbit ? 0xFFFFAA3C : 0xFFF0D2B4; // the rabbit's magma glaze shows as orange streaks
		int left = cx - 8 * SCALE;
		int spriteTop = panY - CUT.length * SCALE + 6;
		for (int y = 0; y < CUT.length; y++) {
			for (int x = 0; x < 16; x++) {
				char c = CUT[y].charAt(x);
				if (c == '.') {
					continue;
				}
				int color = switch (c) {
					case 'k' -> OUTLINE;
					case 'M' -> meatDark;
					case 'f' -> fat;
					default -> meat;
				};
				color = searMark(results, x, y, color);
				graphics.fill(left + x * SCALE, spriteTop + y * SCALE, left + (x + 1) * SCALE, spriteTop + (y + 1) * SCALE, color);
			}
		}

		// what the last press was worth
		if (feedback > 0 && !results.isEmpty()) {
			int last = results.get(results.size() - 1);
			String key = last == 2 ? "perfect" : last == 1 ? "good" : "miss";
			int color = last == 2 ? 0xFF55FF55 : last == 1 ? 0xFFFFDD55 : 0xFFFF5555;
			graphics.centeredText(font, Component.translatable("screen.jaymoskitchen.sear." + key), cx, boxTop + 10, color);
		}
	}

	/** Each hit leaves a diagonal sear stripe; a miss leaves a charred blotch. Outline pixels are left alone. */
	private static int searMark(List<Integer> results, int x, int y, int color) {
		if (color == OUTLINE) {
			return color;
		}
		for (int i = 0; i < results.size(); i++) {
			int column = 2 + i * 3;
			int result = results.get(i);
			if (result > 0 && x == column + y / 2) {
				return result == 2 ? 0xFF2A140A : 0xFF4A2414;
			}
			if (result == 0 && (x == column || x == column + 1) && (y == 3 || y == 4)) {
				return 0xFF141010;
			}
		}
		return color;
	}
}
