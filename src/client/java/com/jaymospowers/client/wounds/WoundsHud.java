package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Wounds;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The body silhouette (bottom-right of the screen): each part of a person coloured by how whole it is, flashing white
 * when it loses health. Shown while any part is hurt, and fading out for {@link #FADE_TICKS} once all are whole. It
 * reads the {@link Wounds#PARTS} attachment (a humanoid's eight parts, in {@code BodyPlan.HUMANOID} order); the vanilla
 * hearts stay.
 */
public final class WoundsHud {
	/** How long it lingers (fading) after every part is whole again. */
	static final int FADE_TICKS = 60;
	/** How long a part flashes white after losing health (ticks). */
	static final int FLASH_TICKS = 8;
	/** Each unit below is this many pixels. */
	static final int SCALE = 2;
	/** Distance from the screen's corner (pixels). */
	static final int MARGIN = 8;
	/** Colours by fraction: above 0.75, 0.5-0.75, 0.25-0.5, above 0, and none (RGB). */
	static final int GOOD = 0x9ACD9A, FAIR = 0xE8D84A, POOR = 0xE8962E, BAD = 0xC83232, GONE = 0x1A1A1A, FLASH = 0xFFFFFF, OUTLINE = 0x000000;

	// the parts in plan order: head, body, left arm, right arm, left leg, right leg, left foot, right foot
	// (x, y, width, height) in units
	private static final int[][] BOXES = {
		{2, 0, 4, 4}, {2, 4, 4, 6}, {0, 4, 2, 6}, {6, 4, 2, 6}, {2, 10, 2, 5}, {4, 10, 2, 5}, {2, 15, 2, 2}, {4, 15, 2, 2},
	};
	private static final int WIDTH = 8, HEIGHT = 17;

	private static int ticks;
	/** The last fractions seen (whole when none), for spotting a drop. */
	private static float[] last = whole();
	private static final int[] FLASHING = new int[BOXES.length];
	/** The tick the attachment was last present. */
	private static int lastPresent = Integer.MIN_VALUE / 2;

	private WoundsHud() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(WoundsHud::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("wounds_hud"), WoundsHud::render);
	}

	private static float[] whole() {
		float[] all = new float[BOXES.length];
		java.util.Arrays.fill(all, 1.0F);
		return all;
	}

	/** The local player's parts, or all whole when they have none stored. */
	private static float[] current(LocalPlayer player) {
		List<Float> parts = player.getAttached(Wounds.PARTS);
		if (parts == null || parts.size() != BOXES.length) {
			return whole();
		}
		float[] out = new float[BOXES.length];
		for (int i = 0; i < out.length; i++) {
			out[i] = Mth.clamp(parts.get(i), 0.0F, 1.0F);
		}
		return out;
	}

	private static void tick(Minecraft minecraft) {
		ticks++;
		LocalPlayer player = minecraft.player;
		if (player == null) {
			last = whole();
			java.util.Arrays.fill(FLASHING, 0);
			return;
		}
		float[] now = current(player);
		for (int i = 0; i < now.length; i++) {
			if (now[i] < last[i] - 0.001F) {
				FLASHING[i] = FLASH_TICKS;
			} else if (FLASHING[i] > 0) {
				FLASHING[i]--;
			}
		}
		last = now;
		if (player.getAttached(Wounds.PARTS) != null) {
			lastPresent = ticks;
		}
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.isCreative() || player.isSpectator()) {
			return;
		}
		int since = ticks - lastPresent;
		if (since > FADE_TICKS) {
			return;
		}
		// hurt: opaque; whole again: fading out
		float alpha = player.getAttached(Wounds.PARTS) != null ? 1.0F : 1.0F - since / (float)FADE_TICKS;
		int a = Mth.clamp((int)(alpha * 255.0F), 8, 255) << 24;
		int x0 = graphics.guiWidth() - MARGIN - WIDTH * SCALE;
		int y0 = graphics.guiHeight() - MARGIN - HEIGHT * SCALE;
		// the outline first, so shared edges stay dark
		for (int[] box : BOXES) {
			graphics.fill(x0 + box[0] * SCALE - 1, y0 + box[1] * SCALE - 1, x0 + (box[0] + box[2]) * SCALE + 1,
				y0 + (box[1] + box[3]) * SCALE + 1, a | OUTLINE);
		}
		for (int i = 0; i < BOXES.length; i++) {
			int[] box = BOXES[i];
			int colour = FLASHING[i] > 0 ? FLASH : colour(last[i]);
			graphics.fill(x0 + box[0] * SCALE, y0 + box[1] * SCALE, x0 + (box[0] + box[2]) * SCALE, y0 + (box[1] + box[3]) * SCALE, a | colour);
		}
	}

	private static int colour(float fraction) {
		if (fraction > 0.75F) {
			return GOOD;
		}
		if (fraction > 0.5F) {
			return FAIR;
		}
		if (fraction > 0.25F) {
			return POOR;
		}
		return fraction > 0.0F ? BAD : GONE;
	}
}
