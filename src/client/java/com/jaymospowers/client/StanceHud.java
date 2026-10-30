package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Stance;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * (0.18.57) Andy: "tarkov does something similar, with a display in the corner of your stance. use that inspiration". A
 * small figure in the bottom-left corner shows how you stand (on your feet, crouched, low crouch, prone), with a notched
 * height bar beside it whose marker slides between the stances. It shows while you're below standing and for
 * {@link #LINGER} ticks after you change stance, then fades.
 * <p>(0.18.65) Andy: "the crouching indicator is cut off screen ... use a smoother animation like the one from MGSV". It
 * was drawn with the hotbar, which Auto HUD slides off the bottom; it's drawn before the chat now, clear of the edge. The
 * figure morphs from one stance to the next along with the marker: each body part slides from where it was to where it
 * goes.
 */
final class StanceHud {
	/** Ticks the figure stays after a change back to standing, and how long it takes to fade in or out. */
	static final int LINGER = 60, FADE = 10;
	/** Pixels per figure unit, and the gap from the screen's edges. */
	static final int SCALE = 2, MARGIN = 6;
	static final int FIGURE_COLOR = 0xE6E6E6, LIT_COLOR = 0xFFD54A, DIM_COLOR = 0x50FFFFFF, BACK_COLOR = 0x60000000;
	/** The marker slides this share of the way to its stance each tick. */
	static final float SLIDE = 0.25F;

	/**
	 * Each stance's figure, facing right, as {x0, y0, x1, y1} boxes in an 18 by 18 grid (feet on the bottom row): prone,
	 * low crouch, crouched, standing. Every figure has the same parts in the same order (head, body, front arm, back arm,
	 * front leg, back leg), so one morphs into the next.
	 */
	private static final int[][][] FIGURES = {
		{{14, 12, 18, 16}, {5, 13, 14, 17}, {14, 16, 17, 18}, {11, 16, 13, 18}, {0, 15, 5, 17}, {1, 16, 5, 18}},
		{{9, 6, 13, 10}, {5, 9, 10, 13}, {10, 10, 14, 12}, {3, 10, 5, 12}, {9, 13, 11, 18}, {4, 13, 7, 18}},
		{{8, 3, 12, 7}, {5, 7, 10, 12}, {10, 7, 12, 12}, {4, 8, 6, 12}, {7, 12, 9, 18}, {4, 12, 6, 18}},
		{{6, 0, 10, 4}, {6, 4, 10, 11}, {10, 5, 11, 11}, {5, 5, 6, 11}, {8, 11, 10, 18}, {6, 11, 8, 18}}
	};

	private static int last = Stance.STAND;
	private static int since = LINGER;
	private static float alpha;
	private static float marker = Stance.STAND, markerLast = Stance.STAND;

	private StanceHud() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(StanceHud::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, JaymosPowers.id("stance"), StanceHud::draw);
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			alpha = 0.0F;
			since = LINGER;
			return;
		}
		int now = StanceScroll.shown(player);
		if (now != last) {
			last = now;
			since = 0;
		} else if (since < LINGER) {
			since++;
		}
		boolean show = now != Stance.STAND || since < LINGER;
		alpha = Mth.clamp(alpha + (show ? 1.0F : -1.0F) / FADE, 0.0F, 1.0F);
		markerLast = marker;
		marker += (now - marker) * SLIDE;
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (alpha <= 0.0F || minecraft.player == null) {
			return;
		}
		int size = 18 * SCALE;
		int x = MARGIN + 2, y = graphics.guiHeight() - MARGIN - 2 - size;
		graphics.fill(x - 2, y - 2, x + size + 10, y + size + 2, fade(BACK_COLOR));
		float at = Mth.clamp(Mth.lerp(delta.getGameTimeDeltaPartialTick(false), markerLast, marker), Stance.PRONE, Stance.STAND);
		// the figure part way between the two stances the marker is between
		int below = Math.min((int) at, Stance.STAND - 1);
		float t = at - below;
		t = t * t * (3.0F - 2.0F * t);
		int[][] from = FIGURES[below], to = FIGURES[below + 1];
		for (int i = 0; i < from.length; i++) {
			graphics.fill(x + px(from[i][0], to[i][0], t), y + px(from[i][1], to[i][1], t), x + px(from[i][2], to[i][2], t),
				y + px(from[i][3], to[i][3], t), fade(0xFF000000 | FIGURE_COLOR));
		}
		// the height bar: a notch per stance (standing at the top) and the marker sliding to yours
		int barX = x + size + 4;
		for (int i = Stance.PRONE; i <= Stance.STAND; i++) {
			int ny = y + notch(i, size);
			graphics.fill(barX, ny, barX + 4, ny + 1, fade(DIM_COLOR));
		}
		int my = y + Math.round(Mth.lerp(at / Stance.STAND, size - 1, 0));
		graphics.fill(barX - 1, my - 1, barX + 5, my + 2, fade(0xFF000000 | LIT_COLOR));
	}

	/** A figure edge part way ({@code t}) from {@code a} to {@code b}, in screen pixels. */
	private static int px(int a, int b, float t) {
		return Math.round(Mth.lerp(t, a, b) * SCALE);
	}

	/** Where stance {@code i}'s notch sits down the bar (standing at the top, prone at the bottom). */
	private static int notch(int i, int size) {
		return Math.round(Mth.lerp((float) i / Stance.STAND, size - 1, 0));
	}

	private static int fade(int argb) {
		int a = Math.round((argb >>> 24) * alpha);
		return (a << 24) | (argb & 0xFFFFFF);
	}
}
