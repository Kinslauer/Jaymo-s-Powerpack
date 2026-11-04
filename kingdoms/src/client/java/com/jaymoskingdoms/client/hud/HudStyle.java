package com.jaymoskingdoms.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * (HUD rework) One look for every HUD piece of Powers, Kingdoms and Kitchen, taken from Wind's chips (the pack's first and
 * most-liked HUD): see-through black plates, a 1 px rim in the piece's own colour when it's ready (grey while it isn't),
 * flat 3 px bars in a black frame, and shadowed vanilla text. Nothing here leans on a resource pack, so it reads the same with
 * Excalibur off (the default) or on. Where each piece goes is {@link HudZones}'.
 * <p>
 * A copy of Jaymo's Powers' {@code client.hud.HudStyle}: keep the three the same.
 */
public final class HudStyle {
	/** Plates: a chip or a bar's frame; a softer one behind lines of text (party, quests, kin sense). */
	public static final int PLATE = 0x90000000, PLATE_SOFT = 0x60000000;
	/** A chip that isn't ready: rim; a locked (not yet mastered) chip: fill and rim. */
	public static final int RIM_DIM = 0xA0505050, LOCKED = 0xB0000000, LOCKED_RIM = 0x60505050;
	/** A bar's empty part, behind the fill. */
	public static final int TRACK = 0x30FFFFFF;
	/** Text: plain, secondary, hints and keys while not ready; gold for something special, red for a warning. */
	public static final int TEXT = 0xFFFFFFFF, DIM = 0xFFA8A8B8, HINT = 0xFF707080, GOLD = 0xFFFFD54A, WARN = 0xFFE04040;
	/** Chip size and the gap between chips. */
	public static final int CHIP = 20, CHIP_GAP = 2;
	/** A labelled bar over the crosshair: width, bar height, and the whole piece's height (label, gap, bar, frame). */
	public static final int CENTRE_BAR_W = 52, BAR_H = 3, LABELLED_BAR_H = 9 + 2 + BAR_H + 1;
	/** A labelled bar in the focus band (struggles, rage): width. */
	public static final int FOCUS_BAR_W = 120;

	private HudStyle() {
	}

	/** Opaque version of an RGB colour (keeps the alpha if one is given). */
	public static int opaque(int color) {
		return (color & 0xFF000000) == 0 ? 0xFF000000 | color : color;
	}

	/** A plate with an optional rim (0: none). */
	public static void plate(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill, int rim) {
		graphics.fill(x, y, x + w, y + h, fill);
		if (rim != 0) {
			graphics.outline(x, y, w, h, rim);
		}
	}

	/** A chip's ground: the plate, rimmed in {@code color} when ready, grey when not. */
	public static void chip(GuiGraphicsExtractor graphics, int x, int y, int color, boolean ready) {
		plate(graphics, x, y, CHIP, CHIP, PLATE, ready ? opaque(color) : RIM_DIM);
	}

	/** A locked chip: dark, a faint rim. */
	public static void lockedChip(GuiGraphicsExtractor graphics, int x, int y) {
		plate(graphics, x, y, CHIP, CHIP, LOCKED, LOCKED_RIM);
	}

	/** A flat bar: black frame round it, a faint track, {@code share} of it filled. */
	public static void bar(GuiGraphicsExtractor graphics, int x, int y, int w, int h, float share, int color) {
		graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, PLATE);
		graphics.fill(x, y, x + w, y + h, TRACK);
		int filled = (int)Math.ceil(w * Math.max(0.0F, Math.min(1.0F, share)));
		if (filled > 0) {
			graphics.fill(x, y, x + filled, y + h, opaque(color));
		}
	}

	/**
	 * A labelled bar, {@code w} wide, its piece's top-left at ({@code x}, {@code y}): the label centred on top, the bar under
	 * it. The piece is {@link #LABELLED_BAR_H} tall.
	 */
	public static void labelledBar(GuiGraphicsExtractor graphics, Font font, Component label, int x, int y, int w, float share, int color) {
		if (label != null) {
			graphics.text(font, label, x + w / 2 - font.width(label) / 2, y, TEXT, true);
		}
		bar(graphics, x, y + 11, w, BAR_H, share, color);
	}

	/** Shadowed text (every HUD line reads over the world). */
	public static void text(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
		graphics.text(font, text, x, y, color, true);
	}

	/** Shadowed text centred on {@code cx}. */
	public static void centred(GuiGraphicsExtractor graphics, Font font, Component text, int cx, int y, int color) {
		graphics.text(font, text, cx - font.width(text) / 2, y, color, true);
	}

	/** Mixes two RGB colours, 0 = a, 1 = b. */
	public static int mix(int a, int b, float t) {
		t = Math.max(0.0F, Math.min(1.0F, t));
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
