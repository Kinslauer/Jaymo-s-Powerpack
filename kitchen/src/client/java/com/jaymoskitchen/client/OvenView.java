package com.jaymoskitchen.client;

import com.jaymoskitchen.cooking.Dish;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Draws the furnace as a pizza oven: a stone mouth, a glowing fire bed, a rack, and the pizza (seen slightly from
 * above) browning as it bakes. It slides in on a peel when the bake starts; smoke rises once it's nearly done.
 */
final class OvenView {
	private static final int SCALE = 6;
	private static final int SLIDE_TICKS = 12;

	private static final int OUTLINE = 0xFF28180C;
	private static final int STONE = 0xFF5E5E5E;
	private static final int STONE_DARK = 0xFF3C3C3C;
	private static final int CAVITY = 0xFF1A120E;

	/** raw colour, baked colour */
	private static final Map<Character, int[]> SHADES = Map.of(
		'c', new int[] {0xFFEEDEB4, 0xFFDEA046},  // crust
		'C', new int[] {0xFFD2BE96, 0xFFB47A32},  // crust underside
		'r', new int[] {0xFFC82823, 0xFFA82A1A},  // beetroot sauce
		'm', new int[] {0xFFAA825F, 0xFF825A3C},  // mushroom
		'l', new int[] {0xFFD8C8A0, 0xFFB4782D},  // bread score lines
		'a', new int[] {0xFFE6D282, 0xFFC8963C},  // apple filling
		'A', new int[] {0xFFC83232, 0xFF96281E}); // apple skin

	private static final String[] PIZZA = {
		"....kkkkkkkk....",
		"..kkcccccccckk..",
		".kcrrmrrrrrmrrck",
		"kcrrrrrmrrrrmrck",
		"kcrmrrrrrmrrrrck",
		".kcrrrmrrrrrrcck",
		"..kkCCCCCCCCkk..",
		"....kkkkkkkk....",
	};

	private static final String[] BREAD = {
		"................",
		"....kkkkkkkk....",
		"..kkcclcclcckk..",
		".kcccclcclccccCk",
		".kccclcclccccCCk",
		".kCccccccccccCCk",
		"..kkCCCCCCCCkk..",
		"....kkkkkkkk....",
	};

	private static final String[] TART = {
		"....kkkkkkkk....",
		"..kkcccccccckk..",
		".kcaAacaAacaAack",
		"kcaaacaaaacaaack",
		"kcccccccccccccck",
		".kcaAacaAacaAcck",
		"..kkCCCCCCCCkk..",
		"....kkkkkkkk....",
	};

	private OvenView() {
	}

	/**
	 * @param done       how baked the pizza is (1.0 = just baked through)
	 * @param heat       the fire's strength right now (1.0 = normal)
	 * @param sinceStart ticks since the pizza went in (for the slide-in)
	 * @param time       animation clock in ticks
	 */
	static void draw(GuiGraphicsExtractor graphics, Dish dish, int cx, int baseY, float done, float heat, float sinceStart,
		float time) {
		String[] sprite = switch (dish) {
			case RUSTIC_BREAD -> BREAD;
			case APPLE_TART -> TART;
			default -> PIZZA;
		};
		// oven body and mouth
		graphics.fill(cx - 112, baseY - 122, cx + 112, baseY + 2, STONE_DARK);
		graphics.fill(cx - 110, baseY - 120, cx + 110, baseY, STONE);
		for (int row = 0; row < 6; row++) {
			int y = baseY - 120 + row * 20;
			graphics.fill(cx - 110, y, cx + 110, y + 1, STONE_DARK);
			int offset = row % 2 == 0 ? 0 : 20;
			for (int x = cx - 110 + offset; x < cx + 110; x += 40) {
				graphics.fill(x, y, x + 1, y + 20, STONE_DARK);
			}
		}
		graphics.fill(cx - 90, baseY - 100, cx + 90, baseY - 14, CAVITY);

		// fire bed glow and flames along the back
		graphics.fillGradient(cx - 90, baseY - 60, cx + 90, baseY - 14, 0x00FF7A20, 0x90FF7A20);
		int col = 0;
		for (int x = cx - 86; x < cx + 86; x += 4, col++) {
			float wave = Mth.sin(time * 0.4F + col * 1.3F) * 0.5F + Mth.sin(time * 0.8F + col * 0.7F) * 0.5F;
			// the fire surges and dies back: flame height follows the heat
			int height = Math.max(3, Math.min(40, Math.round((9 + wave * 5.0F) * heat * heat)));
			int y = baseY - 14;
			graphics.fill(x, y - height, x + 4, y, 0xFFC8321E);
			graphics.fill(x, y - height * 2 / 3, x + 4, y, 0xFFF08228);
			graphics.fill(x, y - height / 3, x + 4, y, 0xFFFFD25A);
		}

		// rack
		int rackY = baseY - 40;
		graphics.fill(cx - 88, rackY, cx + 88, rackY + 2, 0xFF6A6A6A);

		// the pizza, sliding in from the right on its way into the oven
		float slide = Math.max(0.0F, 1.0F - sinceStart / SLIDE_TICKS);
		int left = cx - 8 * SCALE + Math.round(slide * slide * 160.0F);
		int top = rackY - sprite.length * SCALE;
		for (int y = 0; y < sprite.length; y++) {
			String row = sprite[y];
			for (int x = 0; x < 16; x++) {
				char c = row.charAt(x);
				if (c == '.') {
					continue;
				}
				int color = c == 'k' ? OUTLINE : GrillView.color(SHADES.get(c)[0], SHADES.get(c)[1], done);
				graphics.fill(left + x * SCALE, top + y * SCALE, left + (x + 1) * SCALE, top + (y + 1) * SCALE, color);
			}
		}

		// smoke out of the oven mouth as it gets close (light), and once it burns (thick)
		if (slide <= 0.0F) {
			GrillView.drawSmoke(graphics, cx, top - 50, done, time);
		}
	}
}
