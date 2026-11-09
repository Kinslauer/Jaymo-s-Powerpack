package com.jaymoskingdoms.dragons;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import net.minecraft.util.Mth;

/**
 * Aetherion's GeckoLib animations, all keyed in {@code kingdoms/art/aetherion.bbmodel} by
 * {@code kingdoms/tools/gen_aetherion_geo.py}. The move controller follows the dragon's client-side easing: flight,
 * gliding, landing, crashed, sitting, walking or standing. Server-started bites, Cataclysm and tail sweeps use the
 * action controller. Head look, climb pitch and the breath jaw are added by the renderer.
 */
public final class AetherionAnimations {
	/** Over this much of a state easing (0 to 1), its animation is the one played. */
	static final float SHOWN = 0.5F;
	/** Ticks to blend between movement states and into or out of an action. */
	static final int BLEND = 5, ACTION_BLEND = 3;
	/** Aetherion's ordinary wingbeat rate (radians of {@link WindDragon#flapPhase} a tick). */
	static final float WINGBEAT_RATE = 0.32F;
	/** The walk plays at its authored speed at this walk speed, and never slower than the floor. */
	static final float WALK_AT = 0.6F, WALK_FLOOR = 0.4F;

	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	static final RawAnimation SIT = RawAnimation.begin().thenLoop("sit");
	static final RawAnimation CRASH = RawAnimation.begin().thenLoop("crash");
	static final RawAnimation LAND = RawAnimation.begin().thenPlay("land").thenLoop("idle");
	static final RawAnimation FLY = RawAnimation.begin().thenLoop("fly");
	static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("glide");
	static final RawAnimation BITE = RawAnimation.begin().thenPlay("bite");
	static final RawAnimation CATACLYSM = RawAnimation.begin().thenPlay("cataclysm");
	static final RawAnimation SWEEP = RawAnimation.begin().thenPlay("sweep");

	private AetherionAnimations() {
	}

	static void register(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<AetherionDragon>("move", BLEND, AetherionAnimations::move));
		controllers.add(new AnimationController<AetherionDragon>("action", ACTION_BLEND, test -> PlayState.STOP)
			.triggerableAnim("bite", BITE).triggerableAnim("cataclysm", CATACLYSM).triggerableAnim("sweep", SWEEP));
	}

	private static PlayState move(AnimationTest<AetherionDragon> test) {
		AetherionDragon dragon = test.animatable();
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
		} else if (dragon.landAnim > 0.0F) {
			anim = LAND;
		} else if (dragon.sitAnim > SHOWN) {
			anim = SIT;
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
