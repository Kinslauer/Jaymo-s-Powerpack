package com.jaymoskingdoms.client.glowwood;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;

/** Plays keyframed animations outside an AnimationState: looping by age, or at an exact progress. */
public final class CreatureAnim {

	private CreatureAnim() {
	}

	/** A looping animation driven by the entity's age (ticks, partial included). */
	public static void loop(KeyframeAnimation animation, float ageInTicks) {
		loop(animation, ageInTicks, 1.0F, 1.0F);
	}

	/** As {@link #loop(KeyframeAnimation, float)}, with a playback speed and a blend weight. */
	public static void loop(KeyframeAnimation animation, float ageInTicks, float speed, float weight) {
		if (weight <= 0.001F) {
			return;
		}
		animation.apply((long) (ageInTicks * 50.0F * speed), weight);
	}

	/** A one-shot animation at progress 0..1 of its length (e.g. a swell driven by synced progress). */
	public static void at(KeyframeAnimation animation, AnimationDefinition definition, float progress, float weight) {
		if (weight <= 0.001F) {
			return;
		}
		animation.apply((long) (Math.max(0.0F, Math.min(1.0F, progress)) * definition.lengthInSeconds() * 1000.0F), weight);
	}
}
