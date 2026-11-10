package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Blinded (the Blindness effect, from any power or anything else): the view goes dark. Vanilla only pulls its black fog
 * in to 5 blocks, so what's close stays fully lit. This lays darkness over the whole view, {@link #WASH} everywhere and
 * closing to near black at the edges ({@link #RIM}, reaching {@link #REACH} in). It stacks on vanilla's fog, which still
 * hides everything further off. It's the first thing on the HUD, so the crosshair, the hotbar and every other piece
 * from any of the three mods (the Resonance ring, the Blindness power's echo rings, Valor's bar) stay on top of it.
 */
final class BlindedHud {
	/** The darkness over the whole view at full strength (0-1): what's right in front is still just there. */
	static final float WASH = 0.75F;
	/** More on top of it at the very edge (0-1), fading in to nothing this far in (share of the shorter side), in bands. */
	static final float RIM = 0.9F, REACH = 0.42F;
	static final int BANDS = 16;
	/** It closes in over this many ticks once you're blinded (it lifts with the effect's own last second, as vanilla's fog does). */
	static final int FADE_IN_TICKS = 6;

	private static float shade, lastShade;

	private BlindedHud() {
	}

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BlindedHud::tick);
		HudElementRegistry.addFirst(JaymosPowers.id("blinded"), BlindedHud::render);
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.isPaused()) {
			return;
		}
		lastShade = shade;
		LocalPlayer player = minecraft.player;
		float target = player == null || player.isSpectator() ? 0.0F : strength(player.getEffect(MobEffects.BLINDNESS));
		shade = target > shade ? Math.min(target, shade + 1.0F / FADE_IN_TICKS) : target;
	}

	/** Vanilla's own rule for the effect's darkness: full until its last second, then fading with it. */
	private static float strength(MobEffectInstance blind) {
		if (blind == null) {
			return 0.0F;
		}
		return blind.endsWithin(19) ? Mth.clamp(blind.getDuration() / 20.0F, 0.0F, 1.0F) : 1.0F;
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		float s = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), lastShade, shade);
		if (s <= 0.0F) {
			return;
		}
		int w = graphics.guiWidth(), h = graphics.guiHeight();
		graphics.fill(0, 0, w, h, Math.round(255.0F * WASH * s) << 24);
		// the edges: one frame per band, darkest outside
		float band = Math.min(w, h) * REACH / BANDS;
		for (int i = 0; i < BANDS; i++) {
			float t = 1.0F - i / (float)BANDS;
			int alpha = Math.round(255.0F * RIM * s * t * t);
			int a = Math.round(i * band), b = Math.round((i + 1) * band);
			if (alpha <= 0 || b <= a) {
				continue;
			}
			int color = Math.min(255, alpha) << 24;
			graphics.fill(a, a, w - a, b, color);
			graphics.fill(a, h - b, w - a, h - a, color);
			graphics.fill(a, b, b, h - b, color);
			graphics.fill(w - b, b, w - a, h - b, color);
		}
	}
}
