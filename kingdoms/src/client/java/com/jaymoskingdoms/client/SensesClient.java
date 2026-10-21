package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Senses;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * (0.7.103) The awareness gauge ({@link Senses}): a small eye above the crosshair. It fills (from the bottom) as the nearest
 * monster that is noticing you gets close to spotting you, opens red when one has spotted you, and half-closes (amber) while
 * one searches for where you went. The server sends its state a few times a second, only when it changes; the eye fades in
 * and out here. The sheet {@code textures/gui/awareness_eye.png} (made by {@code tools/gen_items.py}) has four 16x16
 * frames: empty, half-closed, open, full.
 */
public final class SensesClient {
	/** The eye's size, and how far above the crosshair's middle its bottom sits (pixels). */
	static final int SIZE = 16, ABOVE = 12;
	/** How fast it fades in and out (per tick). */
	static final float FADE = 0.12F;
	private static final Identifier EYE = JaymosKingdoms.id("textures/gui/awareness_eye.png");
	private static final int FRAMES = 4, EMPTY = 0, HALF = 1, OPEN = 2, FULL = 3;

	/** What the server last said, and how visible the eye is now and a tick ago. */
	private static int state, fill;
	private static float alpha, lastAlpha;

	private SensesClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Senses.Gauge.TYPE, (payload, context) -> context.client().execute(() -> {
			state = payload.state();
			fill = payload.fill();
		}));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			state = fill = 0;
			alpha = lastAlpha = 0.0F;
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			lastAlpha = alpha;
			alpha = state > 0 ? Math.min(1.0F, alpha + FADE) : Math.max(0.0F, alpha - FADE);
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, JaymosKingdoms.id("awareness"), SensesClient::hud);
	}

	private static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		float a = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), lastAlpha, alpha);
		if (a <= 0.01F || minecraft.player == null || minecraft.player.isSpectator()) {
			return;
		}
		int x = graphics.guiWidth() / 2 - SIZE / 2, y = graphics.guiHeight() / 2 - ABOVE - SIZE;
		int color = ARGB.white(a);
		switch (state) {
			case Senses.SPOTTED -> frame(graphics, OPEN, x, y, color);
			case Senses.SEARCHING -> frame(graphics, HALF, x, y, color);
			default -> {
				frame(graphics, EMPTY, x, y, color);
				// (the fill rises from the bottom of the full eye)
				int h = Mth.clamp(Math.round(SIZE * fill / (float) Senses.FILL_STEPS), 0, SIZE);
				if (h > 0) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, EYE, x, y + SIZE - h, 0.0F, (float) (FULL * SIZE + SIZE - h), SIZE, h, SIZE, SIZE * FRAMES, color);
				}
			}
		}
	}

	private static void frame(GuiGraphicsExtractor graphics, int frame, int x, int y, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, EYE, x, y, 0.0F, (float) (frame * SIZE), SIZE, SIZE, SIZE, SIZE * FRAMES, color);
	}
}
