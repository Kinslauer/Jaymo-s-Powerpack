package com.jaymoskitchen.client;

import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Smoke;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Draws the smoker minigame: a heat gauge with the curing band, progress bars, and a smokehouse with the meat
 * hanging over a coal bed. The coals glow with the heat, smoke thickens with it, and a bellows pumps while fanning.
 * The meat darkens from raw to smoked as it cures, and chars if it scorches.
 */
final class SmokerView {
	private static final int SCALE = 5;
	private static final int OUTLINE = 0xFF28180C;

	/** raw colour, fully smoked colour */
	private static final Map<Character, int[]> SHADES = Map.of(
		'p', new int[] {0xFFF09A9A, 0xFFA8442C},  // meat
		'P', new int[] {0xFFD27878, 0xFF7E301E},  // meat shadow
		'f', new int[] {0xFFFFF0E6, 0xFFE6B482},  // fat
		's', new int[] {0xFFC86464, 0xFF6E2814},  // jerky strip
		'S', new int[] {0xFFA85050, 0xFF521C0E},  // jerky shadow
		'h', new int[] {0xFF9A9A9A, 0xFF9A9A9A}); // hook / string

	private static final String[] HAM = {
		".......hh.......",
		".......hh.......",
		"......kkkk......",
		".....kfffPk.....",
		"....kppppppk....",
		"...kpppfppPPk...",
		"...kppppppPPk...",
		"...kpfppppPPk...",
		"...kppppppPPk...",
		"....kppppPPk....",
		".....kpPPPk.....",
		"......kkkk......",
		".......kk.......",
		"......kffk......",
		".......kk.......",
		"................",
	};

	private static final String[] JERKY = {
		"hhhhhhhhhhhhhhhh",
		"..k...k...k...k.",
		".ksk.ksk.ksk.ksk",
		".ksk.kSk.ksk.kSk",
		".kSk.ksk.kSk.ksk",
		".ksk.ksk.ksk.ksk",
		".ksk.kSk.ksk.kSk",
		".kSk.ksk.kSk.ksk",
		".ksk.ksk.ksk.ksk",
		".kSk.ksk.kSk.ksk",
		"..k..ksk..k..kSk",
		"......k.......k.",
		"................",
		"................",
		"................",
		"................",
	};

	private SmokerView() {
	}

	static void draw(GuiGraphicsExtractor graphics, Dish dish, int cx, int top, int barLeft, int barRight, int baseY,
		float heat, boolean fanning, float cure, float scorch, float timeLeft, float time) {
		int barW = barRight - barLeft;

		// heat gauge: cool blue to hot red, with the curing band and the scorch zone marked
		int gTop = top;
		int gBottom = top + 10;
		graphics.fillGradient(barLeft, gTop, barRight, gBottom, 0xFF2A4A8A, 0xFF2A4A8A);
		for (int i = 0; i < barW; i += 2) {
			float t = i / (float) barW;
			graphics.fill(barLeft + i, gTop, barLeft + i + 2, gBottom, heatColor(t));
		}
		int bandL = barLeft + Math.round(barW * Smoke.BAND_MIN);
		int bandR = barLeft + Math.round(barW * Smoke.BAND_MAX);
		graphics.fill(bandL, gTop - 2, bandR, gTop, 0xFF55FF55);
		graphics.fill(bandL, gBottom, bandR, gBottom + 2, 0xFF55FF55);
		graphics.fill(bandL, gTop, bandR, gBottom, 0x5055FF55);
		int scorchX = barLeft + Math.round(barW * Smoke.SCORCH);
		graphics.fill(scorchX, gTop - 2, scorchX + 1, gBottom + 2, 0xFFFF5555);
		int needle = barLeft + Math.round(barW * heat);
		graphics.fill(needle - 1, gTop - 4, needle + 2, gBottom + 4, 0xFFFFFFFF);

		// cure progress (green), scorch (red), time left (grey)
		graphics.fill(barLeft, top + 16, barRight, top + 21, KitchenUi.BAR_BACK);
		graphics.fill(barLeft, top + 16, barLeft + Math.round(barW * Math.min(1.0F, cure)), top + 21, 0xFF55FF55);
		graphics.fill(barLeft, top + 23, barRight, top + 26, KitchenUi.BAR_BACK);
		graphics.fill(barLeft, top + 23, barLeft + Math.round(barW * Math.min(1.0F, scorch)), top + 26, 0xFFFF5555);
		graphics.fill(barLeft, top + 28, barLeft + Math.round(barW * Math.max(0.0F, timeLeft)), top + 30, 0xFF808080);

		// smokehouse
		int boxL = cx - 70;
		int boxR = cx + 70;
		int boxTop = top + 38;
		graphics.fill(boxL - 2, boxTop - 2, boxR + 2, baseY + 2, 0xFF3C3C3C);
		graphics.fill(boxL, boxTop, boxR, baseY, 0xFF1C1612);

		// coal bed: glows with the heat
		int glow = heatColor(heat);
		graphics.fillGradient(boxL, baseY - 40, boxR, baseY - 8, glow & 0x00FFFFFF, (0x90 << 24) | (glow & 0x00FFFFFF));
		for (int x = boxL + 4; x < boxR - 4; x += 8) {
			float flick = Mth.sin(time * 0.3F + x * 0.37F) * 0.5F + 0.5F;
			int ember = lerp(0xFF3A1A10, glow, Mth.clamp(heat * 1.2F * (0.6F + 0.4F * flick), 0.0F, 1.0F));
			graphics.fill(x, baseY - 8, x + 6, baseY - 3, ember);
		}

		// smoke: thicker the hotter it runs
		int puffs = 2 + Math.round(heat * 8);
		for (int i = 0; i < puffs; i++) {
			float phase = (time * 0.02F + i / (float) puffs) % 1.0F;
			int px = boxL + 10 + Math.floorMod(i * 47 + Math.round(Mth.sin(time * 0.05F + i) * 6.0F), boxR - boxL - 30);
			int py = baseY - 12 - Math.round(phase * (baseY - 12 - boxTop - 6));
			int size = 6 + Math.round(phase * 10.0F);
			int alpha = Math.round((heat > Smoke.SCORCH ? 0xB0 : 0x60) * (1.0F - phase));
			int rgb = heat > Smoke.SCORCH ? 0x302828 : 0xBEBEBE;
			graphics.fill(px, py, px + size, py + size, (alpha << 24) | rgb);
		}

		// the meat, hung from the rail
		graphics.fill(boxL, boxTop + 4, boxR, boxTop + 6, 0xFF6A6A6A);
		String[] rows = dish == Dish.BEEF_JERKY ? JERKY : HAM;
		int left = cx - 8 * SCALE;
		int spriteTop = boxTop + 5;
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				char c = rows[y].charAt(x);
				if (c == '.') {
					continue;
				}
				int color;
				if (c == 'k') {
					color = OUTLINE;
				} else {
					int[] shade = SHADES.get(c);
					color = lerp(shade[0], shade[1], Mth.clamp(cure, 0.0F, 1.0F));
					if (c != 'h') {
						color = lerp(color, 0xFF141010, Mth.clamp(scorch, 0.0F, 1.0F) * 0.85F);
					}
				}
				graphics.fill(left + x * SCALE, spriteTop + y * SCALE, left + (x + 1) * SCALE, spriteTop + (y + 1) * SCALE, color);
			}
		}

		// bellows at the side: squeezes while fanning
		int squeeze = fanning ? Math.round((Mth.sin(time * 1.2F) * 0.5F + 0.5F) * 6.0F) : 0;
		int bx = boxR + 8;
		int by = baseY - 34;
		graphics.fill(bx, by + squeeze, bx + 22, by + 20 - squeeze, 0xFF8B5A2B);
		graphics.fill(bx, by + 9, bx + 22, by + 11, 0xFF65401E);
		graphics.fill(bx - 8, by + 8, bx, by + 12, 0xFF6A6A6A);
	}

	/** Gauge colour: blue when cold, through orange, to red hot. */
	private static int heatColor(float t) {
		if (t < 0.5F) {
			return lerp(0xFF2A4A8A, 0xFFE08A28, t * 2.0F);
		}
		return lerp(0xFFE08A28, 0xFFD02818, (t - 0.5F) * 2.0F);
	}

	private static int lerp(int from, int to, float t) {
		int a = Math.round(Mth.lerp(t, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF));
		int r = Math.round(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
