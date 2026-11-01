package com.jaymospowers.client.power.zfighter;

import com.jaymospowers.power.zfighter.ZFighterFakeMoon;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Mth;

/**
 * (Andy, 2026-11-01) A Z Fighter's false moon has taken the moon's place in your sky ({@link ZFighterFakeMoon.Sky}): the
 * sky draws a full moon whatever the phase, larger, tinted the ball's pale colour, easing in and out
 * ({@code ZFighterFakeMoonSkyMixin}). Word comes every half second; without it the sky goes back.
 */
public final class ZFighterFakeMoonSky {
	/** Ticks without word before the sky goes back, and how fast it eases (a share a tick). */
	static final int STALE = 30;
	static final float EASE = 0.08F;
	/** At full, the moon is drawn this much bigger, and tinted this far toward the ball's colour. */
	public static final float SCALE = 1.6F, TINT = 0.7F;

	private static int color = 0xFFFFFF;
	private static float target;
	private static float fade;
	private static int quiet = STALE;

	private ZFighterFakeMoonSky() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ZFighterFakeMoon.Sky.TYPE, (sky, context) -> {
			color = sky.color();
			target = Mth.clamp(sky.fade(), 0.0F, 1.0F);
			quiet = 0;
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null) {
				fade = 0.0F;
				target = 0.0F;
				return;
			}
			if (++quiet > STALE) {
				target = 0.0F;
			}
			fade += (target - fade) * EASE;
			if (fade < 0.002F && target <= 0.0F) {
				fade = 0.0F;
			}
		});
	}

	/** How much the sky's moon is the false one now (0 none). */
	public static float fade() {
		return fade;
	}

	/** The tint to multiply the moon's colour by (r, g, b), at the present fade. */
	public static float[] tint() {
		float t = fade * TINT;
		return new float[] {Mth.lerp(t, 1.0F, (color >> 16 & 0xFF) / 255.0F), Mth.lerp(t, 1.0F, (color >> 8 & 0xFF) / 255.0F),
			Mth.lerp(t, 1.0F, (color & 0xFF) / 255.0F)};
	}
}
