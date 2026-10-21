package com.jaymoskitchen.client.corpse;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;

/**
 * (0.5.10) A pose eased in and out per entity, so it doesn't snap on or off: a weight (0-1) on a slightly underdamped
 * spring, stepped by real time (the same at any frame rate; stepped twice in a frame, it moves by nothing the second time).
 * Kitchen's own (it doesn't build against Jaymo's Powers, which has a fuller one). Render thread only; an entity's
 * entry goes when it has eased out, or when it hasn't been drawn for {@link #FORGET_NANOS}.
 */
final class PoseEase {
	/** The spring: stiffness (per second squared) and damping ratio (under 1 overshoots a little). */
	static final float STIFFNESS = 90.0F, DAMPING = 0.75F;
	/** The most the weight may overshoot past 1. */
	static final float MAX = 1.1F;
	/** Integration step and the longest gap taken in one go (seconds). */
	static final float SUBSTEP = 0.008F, MAX_DT = 0.1F;
	/** Under this a weight easing out is done with. */
	static final float SETTLED = 0.003F;
	/** An entry not drawn this long is dropped; how often that's looked for. */
	static final long FORGET_NANOS = 3_000_000_000L, SWEEP_NANOS = 1_000_000_000L;

	/** One entity's weight. */
	static final class Ease {
		float weight, velocity;
		boolean on;
		long last, changed;

		/** Seconds since the pose last came on (or went off). */
		float age() {
			return (this.last - this.changed) / 1.0E9F;
		}
	}

	private final Map<Integer, Ease> eases = new HashMap<>();
	private long lastSweep;

	/** Steps {@code id}'s weight toward {@code on} and returns it, or null when it's off and has eased out. */
	Ease step(int id, boolean on) {
		long now = System.nanoTime();
		if (now - this.lastSweep > SWEEP_NANOS) {
			this.lastSweep = now;
			this.eases.entrySet().removeIf(entry -> now - entry.getValue().last > FORGET_NANOS);
		}
		Ease ease = this.eases.get(id);
		if (ease == null) {
			if (!on) {
				return null;
			}
			ease = new Ease();
			ease.last = now;
			ease.changed = now;
			this.eases.put(id, ease);
		}
		float dt = Mth.clamp((now - ease.last) / 1.0E9F, 0.0F, MAX_DT);
		ease.last = now;
		if (on != ease.on) {
			ease.on = on;
			ease.changed = now;
		}
		float target = on ? 1.0F : 0.0F;
		float damping = 2.0F * DAMPING * Mth.sqrt(STIFFNESS);
		int steps = Math.max(1, Mth.ceil(dt / SUBSTEP));
		float h = dt / steps;
		for (int i = 0; i < steps; i++) {
			ease.velocity += (STIFFNESS * (target - ease.weight) - damping * ease.velocity) * h;
			ease.weight += ease.velocity * h;
		}
		ease.weight = Mth.clamp(ease.weight, 0.0F, MAX);
		if (!on && ease.weight < SETTLED && Math.abs(ease.velocity) < SETTLED * 10.0F) {
			this.eases.remove(id);
			return null;
		}
		return ease;
	}
}
