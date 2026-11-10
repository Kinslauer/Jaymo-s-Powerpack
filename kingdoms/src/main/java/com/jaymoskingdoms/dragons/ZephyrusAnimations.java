package com.jaymoskingdoms.dragons;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import net.minecraft.util.Mth;

/**
 * Zephyrus's GeckoLib animations, keyed in {@code kingdoms/art/zephyrus.bbmodel} by {@code kingdoms/tools/gen_zephyrus_geo.py}
 * from the old ZephyrusDragonModel's poses. A {@code move} controller picks what it's doing from the dragon's own
 * client-side easing: flying (its wingbeat kept in time with its stroke; beating hard to hover while it breathes),
 * gliding, crashed, swimming, sitting, landing, walking or standing. An {@code action} controller plays the bite and the
 * tail sweep when the server starts them. Where it looks, its pitch with a climb and its jaw at the breath are added in
 * the renderer. Dying, it plays its own death (rolling onto its side) instead of the vanilla flip.
 */
public final class ZephyrusAnimations {
	/** Over this much of an easing (0 to 1), its animation is the one played. */
	static final float SHOWN = 0.5F;
	/** Ticks to blend from one animation to the next, and into and out of a bite or a sweep. */
	static final int BLEND = 5, ACTION_BLEND = 3;
	/** The stroke rates ({@link WindDragon#flapPhase} a tick) the fly, hover and glide loops were keyed at. */
	static final float FLY_RATE = 0.32F, HOVER_RATE = 0.3F, GLIDE_RATE = 0.32F * 0.3F;
	/** The walk's speed: played at its own speed at this walk speed, and never slower than the floor. */
	static final float WALK_AT = 0.6F, WALK_FLOOR = 0.4F;

	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	static final RawAnimation SIT = RawAnimation.begin().thenLoop("sit");
	static final RawAnimation CRASH = RawAnimation.begin().thenLoop("crash");
	static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
	static final RawAnimation LAND = RawAnimation.begin().thenPlay("land").thenLoop("idle");
	static final RawAnimation FLY = RawAnimation.begin().thenLoop("fly");
	static final RawAnimation HOVER = RawAnimation.begin().thenLoop("hover");
	static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("glide");
	static final RawAnimation BITE = RawAnimation.begin().thenPlay("bite");
	static final RawAnimation SWEEP = RawAnimation.begin().thenPlay("sweep");
	static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");

	private ZephyrusAnimations() {
	}

	static void register(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<ZephyrusDragon>("move", BLEND, ZephyrusAnimations::move));
		controllers.add(new AnimationController<ZephyrusDragon>("action", ACTION_BLEND, test -> PlayState.STOP)
			.triggerableAnim("bite", BITE).triggerableAnim("sweep", SWEEP));
	}

	private static PlayState move(AnimationTest<ZephyrusDragon> test) {
		WindDragon dragon = test.animatable();
		float stroke = dragon.flapPhase - dragon.flapPhaseO;
		float speed = 1.0F;
		RawAnimation anim;
		if (dragon.isDeadOrDying()) {
			// it rolls onto its side (the renderer doesn't flip it)
			anim = DEATH;
		} else if (dragon.flyAnim > SHOWN) {
			if (dragon.breathAnim > SHOWN) {
				anim = HOVER;
				speed = stroke / HOVER_RATE;
			} else if (dragon.glideAnim > SHOWN) {
				anim = GLIDE;
				speed = stroke / GLIDE_RATE;
			} else {
				anim = FLY;
				speed = stroke / FLY_RATE;
			}
		} else if (dragon.crashAnim > SHOWN) {
			anim = CRASH;
		} else if (dragon.swimAnim > SHOWN) {
			anim = SWIM;
		} else if (dragon.sitAnim > SHOWN) {
			anim = SIT;
		} else if (dragon.landAnim > 0.0F) {
			// just touched down (it eases from 1 to 0 over about as long as the landing plays)
			anim = LAND;
		} else if (test.isMoving()) {
			anim = WALK;
			speed = Math.max(WALK_FLOOR, dragon.walkAnimation.speed() / WALK_AT);
		} else {
			anim = IDLE;
		}
		test.setControllerSpeed(Mth.clamp(speed, 0.2F, 3.0F));
		return test.setAndContinue(anim);
	}
}
