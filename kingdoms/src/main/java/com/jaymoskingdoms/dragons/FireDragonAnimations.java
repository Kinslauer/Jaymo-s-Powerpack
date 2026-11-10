package com.jaymoskingdoms.dragons;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import net.minecraft.util.Mth;

/**
 * Notus's and Boreas's GeckoLib animations: nyxaris' Fire Dragon (Blockbench Workshop, CC BY 4.0) with the moves of
 * ours in {@code kingdoms/art/fire_dragon.bbmodel} ({@code kingdoms/tools/gen_fire_dragon_geo.py} writes it and the
 * game's files). A {@code move} controller picks what it's doing from the dragon's own client-side easing
 * ({@link WindDragon#flyAnim} and the rest): flying (its wingbeat kept in time with its stroke), gliding, landing,
 * crashed, sitting, walking or standing. An {@code action} controller plays the bite and the tail sweep when the server
 * starts them. Its head turning, its jaw at the breath and its pitch with a climb are added in the renderer.
 */
public final class FireDragonAnimations {
	/** Over this much of an easing (0 to 1), its animation is the one played. */
	static final float SHOWN = 0.5F;
	/** Ticks to blend from one animation to the next. */
	static final int BLEND = 5;
	/** Ticks to blend into and out of a bite or a sweep. */
	static final int ACTION_BLEND = 3;
	/** The wingbeat's stroke rate (radians of {@link WindDragon#flapPhase} a tick) at which it plays at its own speed. */
	static final float WINGBEAT_RATE = Mth.TWO_PI / (1.0833F * 20.0F);
	/** The walk's speed: played at its own speed at this walk speed, and never slower than the floor. */
	static final float WALK_AT = 0.6F, WALK_FLOOR = 0.4F;

	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	static final RawAnimation SIT = RawAnimation.begin().thenLoop("sit");
	static final RawAnimation CRASH = RawAnimation.begin().thenLoop("crash");
	static final RawAnimation LAND = RawAnimation.begin().thenPlay("land").thenLoop("idle");
	static final RawAnimation FLY = RawAnimation.begin().thenLoop("fly");
	static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("glide");
	static final RawAnimation BITE = RawAnimation.begin().thenPlay("bite");
	static final RawAnimation SWEEP = RawAnimation.begin().thenPlay("sweep");

	private FireDragonAnimations() {
	}

	static <T extends WindDragon & GeoEntity> void register(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<T>("move", BLEND, FireDragonAnimations::move));
		controllers.add(new AnimationController<T>("action", ACTION_BLEND, test -> PlayState.STOP).triggerableAnim("bite", BITE).triggerableAnim("sweep", SWEEP));
	}

	private static <T extends WindDragon & GeoEntity> PlayState move(AnimationTest<T> test) {
		WindDragon dragon = test.animatable();
		float speed = 1.0F;
		RawAnimation anim;
		if (dragon.flyAnim > SHOWN) {
			if (dragon.glideAnim > SHOWN) {
				anim = GLIDE;
			} else {
				anim = FLY;
				speed = (dragon.flapPhase - dragon.flapPhaseO) / WINGBEAT_RATE;
			}
		} else if (dragon.crashAnim > SHOWN) {
			anim = CRASH;
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
