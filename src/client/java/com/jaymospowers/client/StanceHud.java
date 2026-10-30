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
 */
final class StanceHud {
	/** Ticks the figure stays after a change back to standing, and how long it takes to fade in or out. */
	static final int LINGER = 60, FADE = 10;
	/** Pixels per figure unit, and the gap from the screen's edges. */
	static final int SCALE = 2, MARGIN = 4;
	static final int FIGURE_COLOR = 0xE6E6E6, LIT_COLOR = 0xFFD54A, DIM_COLOR = 0x50FFFFFF, BACK_COLOR = 0x60000000;
	/** The marker slides this share of the way to its stance each tick. */
	static final float SLIDE = 0.35F;

	/**
	 * Each stance's figure, facing right, as {x0, y0, x1, y1} boxes in an 18 by 18 grid (feet on the bottom row): prone,
	 * low crouch, crouched, standing.
	 */
	private static final int[][][] FIGURES = {
		{{14, 12, 18, 16}, {5, 13, 14, 17}, {0, 14, 5, 17}, {12, 16, 15, 18}},
		{{9, 6, 13, 10}, {5, 9, 10, 13}, {6, 13, 11, 15}, {9, 15, 11, 18}, {4, 13, 6, 18}, {10, 10, 13, 12}},
		{{8, 2, 12, 6}, {5, 6, 10, 11}, {4, 11, 6, 18}, {7, 11, 9, 18}, {10, 7, 13, 9}},
		{{6, 0, 10, 4}, {6, 4, 10, 11}, {10, 5, 11, 10}, {5, 5, 6, 10}, {6, 11, 8, 18}, {8, 11, 10, 18}}
	};

	private static int last = Stance.STAND;
	private static int since = LINGER;
	private static float alpha;
	private static float marker = Stance.STAND, markerLast = Stance.STAND;

	private StanceHud() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(StanceHud::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, JaymosPowers.id("stance"), StanceHud::draw);
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
		for (int[] box : FIGURES[last]) {
			graphics.fill(x + box[0] * SCALE, y + box[1] * SCALE, x + box[2] * SCALE, y + box[3] * SCALE, fade(0xFF000000 | FIGURE_COLOR));
		}
		// the height bar: a notch per stance (standing at the top) and the marker sliding to yours
		int barX = x + size + 4;
		for (int i = Stance.PRONE; i <= Stance.STAND; i++) {
			int ny = y + notch(i, size);
			graphics.fill(barX, ny, barX + 4, ny + 1, fade(DIM_COLOR));
		}
		float at = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), markerLast, marker);
		int my = y + Math.round(Mth.lerp(at / Stance.STAND, size - 1, 0));
		graphics.fill(barX - 1, my - 1, barX + 5, my + 2, fade(0xFF000000 | LIT_COLOR));
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
