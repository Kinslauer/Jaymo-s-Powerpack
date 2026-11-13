package com.jaymospowers.client.packlook;

import com.jaymospowers.client.UiStyle;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Andy picked "our screens too" for the vanilla grey look ({@code /screenlook grey}). Our screens were written for dark
 * panels: white and pale text, gold titles, each power's bright colour. On vanilla's light grey those vanish, so while a
 * grey panel of ours is on screen ({@code UiStyle}, {@code Ui} and {@code KitchenUi} raise a shared flag when they draw
 * one) every text drawn after it this frame is turned into ink: pale colours become vanilla's label grey (the paler, the
 * darker, so a title still outranks a hint), bright ones are darkened until they read, and the drop shadow goes. Dark
 * text (a book's ink) is left alone. Item counts and tooltips keep their own white; they end the inking for the frame.
 *
 * <p>The look is read once at start, as the styles read it, so swapping it changes our screens at the next launch.
 */
public final class GreyInk {
	/** Vanilla's label grey: what white text becomes. */
	private static final int LABEL = 0x40;
	/** Text at least this bright (0..255, perceived) is inked; darker text already reads on grey. */
	private static final int BRIGHT = 120;
	/** A colour this close to grey (largest channel gap) is treated as white-to-grey text, not a colour. */
	private static final int NEAR_GREY = 40;
	/** How bright a coloured title may stay (perceived, 0..255). */
	private static final float INK_LUMA = 88.0F;

	private static final boolean[] FLAG = flag();
	private static int paused;

	private GreyInk() {
	}

	private static boolean[] flag() {
		FabricLoader.getInstance().getObjectShare().putIfAbsent("jaymos:grey_ink", new boolean[1]);
		return FabricLoader.getInstance().getObjectShare().get("jaymos:grey_ink") instanceof boolean[] shared ? shared : new boolean[1];
	}

	private static boolean inking() {
		return UiStyle.GREY && FLAG[0] && paused == 0;
	}

	/** The end of a frame's screen (tooltips and other deferred pieces follow in their own colours). */
	public static void end() {
		FLAG[0] = false;
	}

	/** Around something that must keep its own text colour (an item's count). */
	public static void pause(boolean on) {
		paused = Math.max(0, paused + (on ? 1 : -1));
	}

	public static int color(int color) {
		if (!inking()) {
			return color;
		}
		int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
		float luma = 0.299F * r + 0.587F * g + 0.114F * b;
		if (luma < BRIGHT) {
			return color;
		}
		int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		if (max - min < NEAR_GREY) {
			int v = LABEL + (255 - max) / 2;
			return color & 0xFF000000 | v << 16 | v << 8 | v;
		}
		float k = INK_LUMA / luma;
		return color & 0xFF000000 | Math.round(r * k) << 16 | Math.round(g * k) << 8 | Math.round(b * k);
	}

	public static boolean shadow(boolean shadow) {
		return shadow && !inking();
	}
}
