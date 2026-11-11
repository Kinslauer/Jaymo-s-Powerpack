package com.jaymoskingdoms.films;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import java.util.HashMap;
import java.util.Map;

/**
 * The humanoid move library's names ({@code kingdoms/tools/humanoid_moves.py} writes the animations themselves, into
 * every body that has its bones: the film actor, the draugr). Loops are picked by a body's "move" controller; the rest
 * are started on its "action" controller by name, a held pose staying until the next move or {@code stopTriggeredAnim}.
 */
public final class Moves {
	/** Loops: standing, a fighting stance, a walk, a run, a low sprint, the dead's drag, a rout, climbing out of the ground, hung in the air from cords. */
	public static final String IDLE = "idle", GUARD = "guard", WALK = "walk", RUN = "run", SPRINT = "sprint", SHAMBLE = "shamble", FLEE = "flee",
		CLAW_UP = "claw_up", HANG = "hang";
	/** One-off moves, over when they're done. */
	public static final String[] ONCE = {"swing_across", "swing_down", "swing_back", "block", "stagger", "rise", "cast_right", "cast_left",
		"tree_flip", "dodge_roll"};
	/** Moves that end held. */
	public static final String[] HOLD = {"overhead", "chop", "thrust", "kneel", "kneel_up", "kneel_bow", "maimed", "impaled", "death_back",
		"death_front", "death_drop", "death_headless", "summon", "cast_both", "cast_ward", "cast_high", "cast_blade", "point", "disarm",
		"disarmed", "lunge", "look_about", "sling", "glide"};
	/** How fast each moving loop carries a body of ordinary size at its own pace (blocks a second). */
	private static final Map<String, Double> PACE = Map.of(WALK, 1.4, RUN, 3.5, SPRINT, 5.6, SHAMBLE, 1.6, FLEE, 4.0);
	private static final Map<String, RawAnimation> LOOPS = new HashMap<>();

	private Moves() {
	}

	/** The looping animation {@code name}. */
	public static RawAnimation loop(String name) {
		return LOOPS.computeIfAbsent(name, n -> RawAnimation.begin().thenLoop(n));
	}

	/** How fast to play the loop {@code name} for a body of {@code scale} covering {@code speed} blocks a tick. */
	public static float rate(String name, double speed, double scale) {
		Double pace = PACE.get(name);
		return pace == null ? 1.0F : (float)Math.clamp(speed * 20.0 / (pace * Math.max(0.1, scale)), 0.5, 2.4);
	}

	/** Gives an "action" controller every one-off and held move of the library, each started by its name. */
	/** The cloak coming apart into its ribbons (they wind round the body and fly loose), and winding back over it to set whole: how long each takes (ticks). */
	public static final String CLOAK_PART = "cloak_part", CLOAK_FORM = "cloak_form";
	public static final int CLOAK_PART_TICKS = 12, CLOAK_FORM_TICKS = 14;

	/** Gives a "cloak" controller the cloak's two moves. */
	public static <T extends GeoAnimatable> AnimationController<T> cloak(AnimationController<T> controller) {
		return controller.triggerableAnim(CLOAK_PART, RawAnimation.begin().thenPlay(CLOAK_PART))
			.triggerableAnim(CLOAK_FORM, RawAnimation.begin().thenPlayAndHold(CLOAK_FORM));
	}

	public static <T extends GeoAnimatable> AnimationController<T> actions(AnimationController<T> controller) {
		for (String name : ONCE) {
			controller.triggerableAnim(name, RawAnimation.begin().thenPlay(name));
		}
		for (String name : HOLD) {
			controller.triggerableAnim(name, RawAnimation.begin().thenPlayAndHold(name));
		}
		return controller;
	}
}
