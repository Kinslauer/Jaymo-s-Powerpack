package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.DreamCall;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The dreamer's side of a dream call ({@link DreamCall}): while the call comes, the view lifts off the pillow and tilts up and
 * away ({@link #pitchLift}, applied by {@code DreamCameraMixin}); in a dream, a soft light gathers at the edges of the sight
 * with motes drifting up through it; in a nightmare the edges close in black, with a slow red pulse like a heartbeat.
 */
public final class DreamView {
	/** How far the view tilts up while the call comes (degrees). */
	static final float PAN_DEGREES = 75.0F;
	/** Motes in a dream's light; how deep the nightmare's dark comes in (share of the screen). */
	static final int MOTES = 40;
	static final float NIGHT_EDGE = 0.42F;

	private static int kind = DreamCall.NONE, part = DreamCall.PAN, ticks = 1, age;

	private DreamView() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(DreamCall.Dream.TYPE, (payload, ctx) -> {
			kind = payload.kind();
			part = payload.part();
			ticks = Math.max(1, payload.ticks());
			age = 0;
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (kind != DreamCall.NONE) {
				age++;
				if (minecraft.player == null) {
					kind = DreamCall.NONE;
				}
			}
		});
		HudElementRegistry.attachElementBefore(VanillaHudElements.CROSSHAIR, JaymosPowers.id("dream_view"), DreamView::draw);
	}

	/** How far up the view is tilted now (degrees, 0 when no call is coming). */
	public static float pitchLift(float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (kind == DreamCall.NONE || minecraft.player == null || !minecraft.player.isSleeping()) {
			return 0.0F;
		}
		float t = part == DreamCall.PAN ? Mth.clamp((age + partialTicks) / ticks, 0.0F, 1.0F) : 1.0F;
		return PAN_DEGREES * t * t * (3.0F - 2.0F * t);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (kind == DreamCall.NONE) {
			return;
		}
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		float time = age + delta.getGameTimeDeltaPartialTick(false);
		boolean nightmare = kind == DreamCall.NIGHTMARE;
		float in = part == DreamCall.PAN ? Mth.clamp(time / ticks, 0.0F, 1.0F) : 1.0F;
		if (nightmare && part == DreamCall.INSIDE) {
			// the dark closing in from every side, a heartbeat of red in it
			float beat = 0.5F + 0.5F * Mth.sin(time * 0.25F);
			edges(graphics, w, h, NIGHT_EDGE, 0x000000, 0.92F);
			edges(graphics, w, h, NIGHT_EDGE * 0.5F, 0x5A0000, 0.25F + 0.2F * beat);
			return;
		}
		// the call coming, and a dream's light: soft gold at the edges, motes rising
		edges(graphics, w, h, 0.3F, nightmare ? 0x200010 : 0xFFE8B0, (nightmare ? 0.7F : 0.35F) * in);
		if (!nightmare) {
			for (int k = 0; k < MOTES; k++) {
				int x = (int)((k * 7919L) % Math.max(1, w));
				float rise = (time * (0.6F + (k % 5) * 0.15F) + k * 37) % (h + 20);
				int y = Math.round(h + 10 - rise);
				int alpha = Math.round(160 * in * (0.5F + 0.5F * Mth.sin(time * 0.1F + k)));
				graphics.fill(x, y, x + 2, y + 2, alpha << 24 | 0xFFF2C8);
			}
		}
	}

	/** A band of {@code rgb} round all four edges, fading inward over {@code depth} of the screen, at most {@code alpha}. */
	private static void edges(GuiGraphicsExtractor graphics, int w, int h, float depth, int rgb, float alpha) {
		int steps = 16;
		int dx = Math.max(1, Math.round(w * depth / steps)), dy = Math.max(1, Math.round(h * depth / steps));
		for (int i = 0; i < steps; i++) {
			float a = alpha * (1.0F - i / (float)steps) * (1.0F - i / (float)steps);
			int c = Math.round(Mth.clamp(a, 0.0F, 1.0F) * 255.0F) << 24 | rgb;
			int x0 = i * dx, y0 = i * dy;
			graphics.fill(x0, y0, w - x0, y0 + dy, c);
			graphics.fill(x0, h - y0 - dy, w - x0, h - y0, c);
			graphics.fill(x0, y0 + dy, x0 + dx, h - y0 - dy, c);
			graphics.fill(w - x0 - dx, y0 + dy, w - x0, h - y0 - dy, c);
		}
	}
}
