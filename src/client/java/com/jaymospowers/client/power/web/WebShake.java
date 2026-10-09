package com.jaymospowers.client.power.web;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * A short screen shake (Living Armor landing hard): a jolt of {@code strength} degrees that rattles and dies away over
 * {@link #DURATION_MS}. Applied to the camera with the hurt bob (see the GameRenderer mixin).
 */
public final class WebShake {
	private static final long DURATION_MS = 700;
	private static float strength;
	private static long started;

	private WebShake() {
	}

	public static void start(float amount) {
		long now = System.currentTimeMillis();
		// a bigger jolt takes over; a smaller one adds to what's left
		strength = Math.max(amount, current(now) + amount * 0.5F);
		started = now;
	}

	private static float current(long now) {
		float t = (now - started) / (float)DURATION_MS;
		return t >= 1.0F ? 0.0F : strength * (1.0F - t) * (1.0F - t);
	}

	public static void apply(PoseStack poseStack) {
		long now = System.currentTimeMillis();
		float s = current(now);
		if (s <= 0.01F) {
			return;
		}
		float t = (now % 100000L) * 0.001F;
		poseStack.rotateDegrees(Axis.XP, s * (float)Math.sin(t * 61.0));
		poseStack.rotateDegrees(Axis.YP, s * 0.6F * (float)Math.sin(t * 47.0 + 1.3));
		poseStack.rotateDegrees(Axis.ZP, s * 0.8F * (float)Math.sin(t * 53.0 + 2.1));
	}
}
