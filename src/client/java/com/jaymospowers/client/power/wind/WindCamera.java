package com.jaymospowers.client.power.wind;

import net.minecraft.util.Mth;

/**
 * Purely visual camera effects for the local flyer: a decaying shake (take-off, sonic boom, impacts) and a speed FOV
 * boost. Applied by {@code CameraMixin} and {@code AbstractClientPlayerMixin}; the player's real aim is never touched.
 */
public final class WindCamera {
	/** Shake ceiling, in degrees, whatever asks for it. */
	private static final float MAX_SHAKE = 3.0F;
	/** Extra FOV at top cruise speed (0.15 = +15%), scaled by the FOV-effects slider. */
	static final float MAX_FOV_BOOST = 0.15F;

	private static float shakeStrength;
	private static int shakeTicks;
	private static int shakeDuration = 1;
	/** A steady tremble on top (sonic flight), degrees. */
	private static float tremble;
	private static float fovBoost;
	private static int ticks;

	private WindCamera() {
	}

	/** Shake the camera by up to {@code degrees}, fading out over {@code duration} ticks. A weaker shake never cuts a stronger one short. */
	public static void shake(float degrees, int duration) {
		float remaining = shakeTicks > 0 ? shakeStrength * shakeTicks / (float)shakeDuration : 0.0F;
		if (degrees >= remaining) {
			shakeStrength = Math.min(degrees, MAX_SHAKE);
			shakeDuration = Math.max(1, duration);
			shakeTicks = shakeDuration;
		}
	}

	static void setTremble(float degrees) {
		tremble = degrees;
	}

	/** 0..1 share of {@link #MAX_FOV_BOOST}. */
	static void setFovTarget(float target) {
		fovBoost += (target * MAX_FOV_BOOST - fovBoost) * 0.15F;
	}

	static void tick() {
		ticks++;
		if (shakeTicks > 0) {
			shakeTicks--;
		}
	}

	static void reset() {
		shakeTicks = 0;
		tremble = 0.0F;
		fovBoost = 0.0F;
	}

	/** {yaw, pitch} offsets in degrees for this frame, or null when the camera is still. */
	public static float[] offsets(float partialTicks) {
		float decay = shakeTicks > 0 ? Math.max(0.0F, (shakeTicks - partialTicks) / shakeDuration) : 0.0F;
		float amplitude = shakeStrength * decay * decay + tremble;
		if (amplitude < 0.01F) {
			return null;
		}
		float t = ticks + partialTicks;
		float yaw = amplitude * (Mth.sin(t * 2.9F) * 0.6F + Mth.sin(t * 5.3F + 1.2F) * 0.4F);
		float pitch = amplitude * (Mth.sin(t * 3.7F + 2.1F) * 0.6F + Mth.sin(t * 6.1F + 0.4F) * 0.4F);
		return new float[] {yaw, pitch};
	}

	/** Multiplier for the field of view (1 = none), before the FOV-effects slider. Read once a tick by the camera, which smooths it. */
	public static float fovMultiplier() {
		return 1.0F + fovBoost;
	}
}
