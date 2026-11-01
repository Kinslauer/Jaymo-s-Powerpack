package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.VampirePlayers;
import com.jaymoskingdoms.Vampires;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * (0.7.471) Andy: "veins should encroach on your screen the thirstier you get". A vampire player's blood thirst shows as
 * dark veins creeping in from the screen's edges: stubs at the corners soon after they last fed, reaching further in as
 * the thirst grows ({@link Vampires#pallor}), and as far as they go (about two thirds of the way in, the middle left
 * clear) once they're starving or the blood fever burns ({@link VampirePlayers#starving}, {@link com.jaymoskingdoms.BloodFever#burning}).
 * They swell darker with the vampire's own heartbeat ({@link BloodVision#beat}) and draw back over a few seconds after
 * a drink. Dhampirs never thirst, so never see them. Only the vampire sees any of it.
 *
 * <p>The art is six layers ({@code textures/gui/thirst_veins_1..6.png}, drawn by {@code tools/gen_thirst_veins.py}), each
 * the next ring of growth inward; thirst picks how many are drawn, the last one faded in part way.
 */
public final class ThirstVeins {
	/** The growth layers, outermost first. */
	static final int LAYERS = 6;
	/** Thirst below this shows nothing (just fed); the veins' opacity at the faintest and at full thirst. */
	static final float SHOW_FROM = 0.04F, ALPHA_FAINT = 0.55F, ALPHA_FULL = 0.92F;
	/** How fast the veins creep in and draw back (thirst per tick): slow in, quicker out after a drink. */
	static final float CREEP = 0.004F, RECEDE = 0.015F;
	/** How much the veins darken at rest (colour multiplier) and swell back to full colour on each heartbeat. */
	static final float REST_SHADE = 0.72F, BEAT_SWELL = 0.28F;

	private static final Identifier[] TEXTURES = new Identifier[LAYERS];

	static {
		for (int i = 0; i < LAYERS; i++) {
			TEXTURES[i] = JaymosKingdoms.id("textures/gui/thirst_veins_" + (i + 1) + ".png");
		}
	}

	/** Thirst shown (0-1), eased toward the real one. */
	private static float shown, lastShown;

	private ThirstVeins() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ThirstVeins::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.MISC_OVERLAYS, JaymosKingdoms.id("thirst_veins"), ThirstVeins::draw);
	}

	/** How thirsty this player is (0-1), or 0 for anyone who doesn't thirst. */
	static float thirst(LocalPlayer player) {
		if (!Vampires.isVampire(player) || Vampires.dhampir(player) || player.isSpectator()) {
			return 0.0F;
		}
		if (VampirePlayers.starving(player) || com.jaymoskingdoms.BloodFever.burning(player)) {
			return 1.0F;
		}
		return Vampires.pallor(player);
	}

	private static void tick(Minecraft minecraft) {
		lastShown = shown;
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) {
			shown = lastShown = 0.0F;
			return;
		}
		float target = thirst(player);
		shown = target > shown ? Math.min(target, shown + CREEP) : Math.max(target, shown - RECEDE);
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float partialTicks = delta.getGameTimeDeltaPartialTick(false);
		float t = Mth.lerp(partialTicks, lastShown, shown);
		if (t <= SHOW_FROM) {
			return;
		}
		float reach = (t - SHOW_FROM) / (1.0F - SHOW_FROM) * LAYERS;
		float alpha = Mth.lerp(t, ALPHA_FAINT, ALPHA_FULL);
		float shade = Math.min(1.0F, REST_SHADE + BEAT_SWELL * BloodVision.beat(partialTicks));
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		for (int i = 0; i < LAYERS; i++) {
			float part = Mth.clamp(reach - i, 0.0F, 1.0F);
			if (part <= 0.0F) {
				break;
			}
			int color = ARGB.colorFromFloat(alpha * part, shade, shade, shade);
			graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURES[i], 0, 0, 0.0F, 0.0F, w, h, w, h, color);
		}
	}
}
