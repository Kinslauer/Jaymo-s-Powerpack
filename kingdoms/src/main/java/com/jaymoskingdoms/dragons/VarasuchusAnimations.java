package com.jaymoskingdoms.dragons;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import net.minecraft.util.Mth;

/**
 * Varasuchus's GeckoLib animations, keyed from its old model's poses in {@code kingdoms/art/varasuchus.bbmodel} by
 * {@code kingdoms/tools/gen_varasuchus_geo.py}. It doesn't fly, so there's no flying, gliding or landing. A {@code move}
 * controller picks what it's doing from its client-side easing: crashed, swimming, sitting, upright (raging, stood or
 * walking on its hind legs), walking or galloping, or stood still, when every 15 seconds it hisses, shakes its head,
 * swishes its tail or sniffs, in turn. An {@code action} controller plays the moves the server starts
 * ({@link VarasuchusDragon#action}), each as {@code <move>_up} when it's upright, and a flinch and its death.
 */
public final class VarasuchusAnimations {
	static final float SHOWN = 0.5F;
	static final int BLEND = 5, ACTION_BLEND = 3;
	/** The walk speeds the walk and the gallop are keyed at, and over which it gallops. */
	static final float WALK_AT = 0.45F, RUN_AT = 0.9F, RUN_WHEN = 0.7F;
	/** The swim is keyed treading still; under way its strokes quicken as the old model's did. */
	static final float STROKE = 0.08F, STROKE_RUN = 0.14F;
	/** The idle cycle (ticks) and how far into it a dragon starts (as the old model's). */
	static final int IDLE_TICKS = 300, IDLE_OFFSET = 140;

	static final RawAnimation[] IDLES = {RawAnimation.begin().thenLoop("idle_hiss"), RawAnimation.begin().thenLoop("idle_shake"),
		RawAnimation.begin().thenLoop("idle_swish"), RawAnimation.begin().thenLoop("idle_sniff")};
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
	static final RawAnimation STAND = RawAnimation.begin().thenLoop("stand");
	static final RawAnimation STAND_WALK = RawAnimation.begin().thenLoop("stand_walk");
	static final RawAnimation SIT = RawAnimation.begin().thenLoop("sit");
	static final RawAnimation CRASH = RawAnimation.begin().thenLoop("crash");
	static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
	static final RawAnimation HURT = RawAnimation.begin().thenPlay("hurt");
	static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");

	private VarasuchusAnimations() {
	}

	static void register(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<VarasuchusDragon>("move", BLEND, VarasuchusAnimations::move));
		AnimationController<VarasuchusDragon> action = new AnimationController<VarasuchusDragon>("action", ACTION_BLEND, test -> PlayState.STOP)
			.triggerableAnim("hurt", HURT).triggerableAnim("death", DEATH);
		for (int move = VarasuchusDragon.CLAW_MOVE; move <= VarasuchusDragon.SWEEP_MOVE; move++) {
			String name = name(move);
			action.triggerableAnim(name, RawAnimation.begin().thenPlay(name));
			action.triggerableAnim(name + "_up", RawAnimation.begin().thenPlay(name + "_up"));
		}
		controllers.add(action);
	}

	/** A move's animation: its name, with {@code _up} when it's made upright. */
	static String name(int move, boolean upright) {
		return name(move) + (upright ? "_up" : "");
	}

	private static String name(int move) {
		return switch (move) {
			case VarasuchusDragon.CLAW_MOVE -> "claw";
			case VarasuchusDragon.HORN_MOVE -> "horn";
			case VarasuchusDragon.DASH_MOVE -> "dash";
			case VarasuchusDragon.BARRAGE_MOVE -> "barrage";
			case VarasuchusDragon.SHIFT_MOVE -> "shift";
			case VarasuchusDragon.GUARD_MOVE -> "guard";
			case VarasuchusDragon.PARRY_MOVE -> "parry";
			case VarasuchusDragon.RAGE_MOVE -> "rage";
			case VarasuchusDragon.BITE_MOVE -> "bite";
			case VarasuchusDragon.SWEEP_MOVE -> "sweep";
			default -> throw new IllegalArgumentException("Unknown Varasuchus move " + move);
		};
	}

	private static PlayState move(AnimationTest<VarasuchusDragon> test) {
		VarasuchusDragon dragon = test.animatable();
		float walk = dragon.walkAnimation.speed();
		float speed = 1.0F;
		RawAnimation anim;
		if (dragon.crashAnim > SHOWN) {
			anim = CRASH;
		} else if (dragon.swimAnim > SHOWN) {
			anim = SWIM;
			speed = (STROKE + Math.min(walk, 1.0F) * STROKE_RUN) / STROKE;
		} else if (dragon.sitAnim > SHOWN) {
			anim = SIT;
		} else if (dragon.standAnim > SHOWN) {
			anim = test.isMoving() ? STAND_WALK : STAND;
			if (test.isMoving()) {
				speed = Math.max(0.4F, walk / WALK_AT);
			}
		} else if (test.isMoving()) {
			boolean running = walk > RUN_WHEN;
			anim = running ? RUN : WALK;
			speed = Math.max(0.4F, walk / (running ? RUN_AT : WALK_AT));
		} else {
			anim = IDLES[Math.floorMod(Math.floorDiv(dragon.tickCount + IDLE_OFFSET, IDLE_TICKS), IDLES.length)];
		}
		test.setControllerSpeed(Mth.clamp(speed, 0.2F, 3.0F));
		return test.setAndContinue(anim);
	}
}
