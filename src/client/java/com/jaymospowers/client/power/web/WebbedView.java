package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.ScreenShake;
import com.jaymospowers.power.web.Webbed;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Webbed up, from inside ({@link Webbed}; the medieval picks' de-firstperson, our own idea on top of Derelict's): strands
 * of web cling round the edges of your view, one sheet a layer (the thin one at the corners, the dense one reaching well
 * in), thinning out as the layers come off. No text. Struggling is your walking keys: each fresh press tears at it (a
 * jolt of the view, and the strands shiver), and {@link Webbed#STRUGGLE_PRESSES} of them tear a layer off.
 */
public final class WebbedView {
	/** How fast the sheets come and go (layers a tick). */
	static final float FADE = 0.12F;
	/** How strong the sheets are at full (0-1). */
	static final float ALPHA = 0.92F;
	/** A struggle's jolt of the view (degrees), and how far (pixels) and how long (ticks) the strands shiver. */
	static final float STRUGGLE_SHAKE = 0.3F, SHIVER = 3.0F;
	static final int SHIVER_TICKS = 4;

	static final Identifier[] SHEETS = {
		JaymosPowers.id("textures/gui/webbed/strands_thin.png"),
		JaymosPowers.id("textures/gui/webbed/strands_thick.png"),
		JaymosPowers.id("textures/gui/webbed/strands_dense.png"),
	};

	private static float shown, lastShown;
	private static int shiver;
	private static final boolean[] WAS_DOWN = new boolean[4];

	private WebbedView() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(WebbedView::tick);
		HudElementRegistry.addFirst(JaymosPowers.id("webbed"), WebbedView::render);
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.isPaused()) {
			return;
		}
		lastShown = shown;
		LocalPlayer player = minecraft.player;
		int layers = player == null || player.isSpectator() ? 0 : Webbed.shown(player);
		shown = shown < layers ? Math.min(layers, shown + FADE * 3.0F) : Math.max(layers, shown - FADE);
		if (shiver > 0) {
			shiver--;
		}
		KeyMapping[] keys = {minecraft.options.keyUp, minecraft.options.keyDown, minecraft.options.keyLeft, minecraft.options.keyRight};
		boolean fresh = false;
		for (int i = 0; i < keys.length; i++) {
			boolean down = keys[i].isDown();
			fresh |= down && !WAS_DOWN[i];
			WAS_DOWN[i] = down;
		}
		if (fresh && layers > 0 && minecraft.gui.screen() == null && ClientPlayNetworking.canSend(Webbed.Struggle.TYPE)) {
			ClientPlayNetworking.send(new Webbed.Struggle());
			ScreenShake.add(STRUGGLE_SHAKE);
			shiver = SHIVER_TICKS;
		}
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		float s = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), lastShown, shown);
		if (s <= 0.0F || !minecraft.options.getCameraType().isFirstPerson()) {
			return;
		}
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		int dx = 0, dy = 0;
		if (shiver > 0 && minecraft.player != null) {
			float t = minecraft.player.tickCount + delta.getGameTimeDeltaPartialTick(false);
			dx = Math.round(Mth.sin(t * 2.7F) * SHIVER * shiver / SHIVER_TICKS);
			dy = Math.round(Mth.cos(t * 3.1F) * SHIVER * 0.6F * shiver / SHIVER_TICKS);
		}
		for (int i = 0; i < SHEETS.length; i++) {
			float part = Mth.clamp(s - i, 0.0F, 1.0F);
			if (part <= 0.0F) {
				break;
			}
			// (each sheet a little larger than the screen, so a shiver never shows its edge)
			graphics.blit(RenderPipelines.GUI_TEXTURED, SHEETS[i], dx - 4, dy - 4, 0.0F, 0.0F, w + 8, h + 8, w + 8, h + 8,
				ARGB.colorFromFloat(ALPHA * part, 1.0F, 1.0F, 1.0F));
		}
	}
}
