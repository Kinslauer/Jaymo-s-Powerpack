package com.jaymospowers.client.power.web;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * A weight per entity that eases toward a target instead of snapping to it (Andy: "there should be a smooth transition
 * for all animations"): the render state only says what a player is now (upright, mouth open...), so the client keeps
 * the weight between frames and moves it toward the target at a set pace, on real time (so it's the same at any frame
 * rate, as WebFeral's sprint lean is). One of these per thing that blends.
 */
final class WebEase {
	/** No frame counts for more than this many seconds (a stall, or the first frame after a long gap). */
	private static final float LONGEST_STEP = 0.25F;

	private static final class Weight {
		float value;
		long at;
	}

	private final Map<Integer, Weight> weights = new HashMap<>();

	/**
	 * Moves this entity's weight toward {@code target} (0-1) and returns it: a full change takes {@code ticks} ticks (a
	 * fresh entity starts at its target, so nothing blends on the first frame it's seen).
	 */
	float to(int id, float target, float ticks) {
		long now = System.nanoTime();
		Weight weight = this.weights.computeIfAbsent(id, key -> {
			Weight fresh = new Weight();
			fresh.value = target;
			fresh.at = now;
			return fresh;
		});
		float seconds = Math.min(LONGEST_STEP, (now - weight.at) / 1.0E9F);
		weight.at = now;
		float step = seconds * 20.0F / Math.max(ticks, 0.01F);
		weight.value = target > weight.value ? Math.min(target, weight.value + step) : Math.max(target, weight.value - step);
		if (this.weights.size() > 64) {
			Minecraft minecraft = Minecraft.getInstance();
			this.weights.keySet().removeIf(key -> key != id && (minecraft.level == null || minecraft.level.getEntity(key) == null));
		}
		return weight.value;
	}

	/** This entity's weight as last moved (or {@code fallback} if it hasn't been). */
	float of(int id, float fallback) {
		Weight weight = this.weights.get(id);
		return weight == null ? fallback : weight.value;
	}

	/** 0 → 1 with a soft start and end. */
	static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
