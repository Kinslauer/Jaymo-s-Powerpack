package com.jaymospowers.client.rig;

import net.minecraft.util.Mth;

/**
 * Keyed tracks for the Rig's poses ({@link RigPose}): a move is a few keys, each a time (ticks into the move), a value
 * and the ease the track arrives at it with, the way a hand-keyed clip is built, so a move reads as wind-up, release and
 * settle instead of one curve up and down. Before the first key a track holds its value, after the last it holds that one.
 */
final class RigBeats {
	/** Eases: straight; fast then slowing; past the key and back (an overshoot); slow, fast, slow; slow then fast. */
	static final float LINEAR = 0.0F, OUT = 1.0F, BACK = 2.0F, SINE = 3.0F, IN = 4.0F;
	/** How far {@link #BACK} runs past its key (1.70158 is the usual tenth over). */
	static final float OVERSHOOT = 1.70158F;

	private RigBeats() {
	}

	/** The track's value at {@code t}; {@code keys} are triples: time, value, the ease into it. */
	static float at(float t, float... keys) {
		if (t <= keys[0]) {
			return keys[1];
		}
		for (int i = 3; i < keys.length; i += 3) {
			if (t < keys[i]) {
				float span = keys[i] - keys[i - 3];
				float x = span <= 0.0F ? 1.0F : (t - keys[i - 3]) / span;
				return Mth.lerp(ease(x, keys[i + 2]), keys[i - 2], keys[i + 1]);
			}
		}
		return keys[keys.length - 2];
	}

	static float ease(float x, float kind) {
		if (kind == OUT) {
			float inv = 1.0F - x;
			return 1.0F - inv * inv * inv;
		}
		if (kind == BACK) {
			float inv = x - 1.0F;
			return 1.0F + (OVERSHOOT + 1.0F) * inv * inv * inv + OVERSHOOT * inv * inv;
		}
		if (kind == SINE) {
			return 0.5F - 0.5F * Mth.cos(x * Mth.PI);
		}
		if (kind == IN) {
			return x * x;
		}
		return x;
	}
}
