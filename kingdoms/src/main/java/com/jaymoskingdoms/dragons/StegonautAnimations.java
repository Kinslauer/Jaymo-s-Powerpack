package com.jaymoskingdoms.dragons;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import net.minecraft.util.Mth;

/**
 * Stegonaut's GeckoLib animations, keyed from its old model's pose equations into {@code kingdoms/art/stegonaut.bbmodel}
 * by {@code kingdoms/tools/gen_stegonaut_geo.py}. It never flies, so it has no fly, glide or land. A {@code move}
 * controller picks the state from the dragon's client-side easing (crashed, swimming, sitting, walking or galloping,
 * standing with a grumble now and then); a {@code hurt} controller plays the flinch; an {@code action} controller plays
 * the moves the server starts (chin slam, ground slam, ground eating, roar, bite, grazing, tail sweep) and the death.
 * Each move keys only the bones it uses, so the legs go on walking under a bite.
 */
public final class StegonautAnimations {
	/** Over this much of an easing (0 to 1), its animation is the one played. */
	static final float SHOWN = 0.5F;
	static final int BLEND = 5, ACTION_BLEND = 3;
	/** The walk speeds the walk and the gallop are keyed at, and the speed it breaks into a gallop. */
	static final float WALK_AT = 0.5F, RUN_AT = 1.0F, RUN_WHEN = 0.72F;
	/** One idle loop (ticks; a grumble opens each) and how the grumbles fall, as the old model had them. */
	static final int IDLE_TICKS = 260, IDLE_OFFSET = 100;

	static final RawAnimation IDLE_TOSS = RawAnimation.begin().thenLoop("idle_toss");
	static final RawAnimation IDLE_TILT = RawAnimation.begin().thenLoop("idle_tilt");
	static final RawAnimation IDLE_RAISE = RawAnimation.begin().thenLoop("idle_raise");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
	static final RawAnimation SIT = RawAnimation.begin().thenLoop("sit");
	static final RawAnimation CRASH = RawAnimation.begin().thenLoop("crash");
	static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
	static final RawAnimation HURT = RawAnimation.begin().thenPlay("hurt");
	static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");

	private StegonautAnimations() {
	}

	static void register(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<StegonautDragon>("move", BLEND, StegonautAnimations::move));
		controllers.add(new AnimationController<StegonautDragon>("hurt", ACTION_BLEND, test -> PlayState.STOP).triggerableAnim("hurt", HURT));
		AnimationController<StegonautDragon> action = new AnimationController<StegonautDragon>("action", ACTION_BLEND, test -> PlayState.STOP)
			.triggerableAnim("death", DEATH).triggerableAnim("sweep", RawAnimation.begin().thenPlay("sweep"));
		for (int move = StegonautDragon.CHIN_SLAM; move <= StegonautDragon.GRAZE; move++) {
			action.triggerableAnim(name(move), RawAnimation.begin().thenPlay(name(move)));
		}
		controllers.add(action);
	}

	/** The animation of one of its moves ({@link StegonautDragon#action}). */
	static String name(int move) {
		return switch (move) {
			case StegonautDragon.CHIN_SLAM -> "chin_slam";
			case StegonautDragon.GROUND_SLAM -> "ground_slam";
			case StegonautDragon.CHEW -> "chew";
			case StegonautDragon.ROAR -> "roar";
			case StegonautDragon.BITE_MOVE -> "bite";
			case StegonautDragon.GRAZE -> "graze";
			default -> throw new IllegalArgumentException("Unknown Stegonaut move " + move);
		};
	}

	private static PlayState move(AnimationTest<StegonautDragon> test) {
		StegonautDragon dragon = test.animatable();
		float speed = 1.0F;
		RawAnimation anim;
		if (dragon.crashAnim > SHOWN) {
			anim = CRASH;
		} else if (dragon.swimAnim > SHOWN) {
			anim = SWIM;
		} else if (dragon.sitAnim > SHOWN) {
			anim = SIT;
		} else if (test.isMoving()) {
			boolean running = dragon.walkAnimation.speed() > RUN_WHEN;
			anim = running ? RUN : WALK;
			speed = Math.max(0.4F, dragon.walkAnimation.speed() / (running ? RUN_AT : WALK_AT));
		} else {
			// a grumble opens each loop, one of three in turn: a head toss, a head tilt, the neck lifted
			anim = switch (Math.floorMod(Math.floorDiv(dragon.tickCount + IDLE_OFFSET, IDLE_TICKS), 3)) {
				case 0 -> IDLE_TOSS;
				case 1 -> IDLE_TILT;
				default -> IDLE_RAISE;
			};
		}
		test.setControllerSpeed(Mth.clamp(speed, 0.2F, 3.0F));
		return test.setAndContinue(anim);
	}
}
