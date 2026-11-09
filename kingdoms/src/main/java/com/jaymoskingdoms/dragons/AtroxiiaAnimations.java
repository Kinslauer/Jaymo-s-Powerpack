package com.jaymoskingdoms.dragons;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import net.minecraft.util.Mth;

/**
 * Atroxiia's GeckoLib animations, keyed from its old model's equations in {@code kingdoms/art/atroxiia.bbmodel} by
 * {@code kingdoms/tools/gen_atroxiia_geo.py}. This walker/swimmer has no flight states. Movement follows its client-side
 * easing; the server-started slam, swipe, precise strike, Gungnir, sweep, quake, bite and lash use the action controller.
 */
public final class AtroxiiaAnimations {
	static final float SHOWN = 0.5F;
	static final int BLEND = 5, ACTION_BLEND = 3;
	static final float WALK_AT = 0.45F, RUN_AT = 0.9F, SWIM_AT = 0.5F, RUN_WHEN = 0.65F;
	static final int IDLE_TICKS = 320, IDLE_OFFSET = 120;

	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	static final RawAnimation IDLE_TAUNT = RawAnimation.begin().thenLoop("idle_taunt");
	static final RawAnimation IDLE_SNIFF = RawAnimation.begin().thenLoop("idle_sniff");
	static final RawAnimation IDLE_GRUMBLE = RawAnimation.begin().thenLoop("idle_grumble");
	static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
	static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
	static final RawAnimation SIT = RawAnimation.begin().thenLoop("sit");
	static final RawAnimation CRASH = RawAnimation.begin().thenLoop("crash");
	static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
	static final RawAnimation HURT = RawAnimation.begin().thenPlay("hurt");
	static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");

	private AtroxiiaAnimations() {
	}

	static void register(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<AtroxiiaDragon>("move", BLEND, AtroxiiaAnimations::move));
		AnimationController<AtroxiiaDragon> action = new AnimationController<AtroxiiaDragon>("action", ACTION_BLEND, test -> PlayState.STOP)
			.triggerableAnim("hurt", HURT).triggerableAnim("death", DEATH);
		for (int move = AtroxiiaDragon.SLAM_MOVE; move <= AtroxiiaDragon.LASH_MOVE; move++) {
			action.triggerableAnim(name(move), RawAnimation.begin().thenPlay(name(move)));
		}
		controllers.add(action);
	}

	private static String name(int move) {
		return switch (move) {
			case AtroxiiaDragon.SLAM_MOVE -> "slam";
			case AtroxiiaDragon.SWIPE_MOVE -> "swipe";
			case AtroxiiaDragon.STRIKE_MOVE -> "strike";
			case AtroxiiaDragon.GUNGNIR_MOVE -> "gungnir";
			case AtroxiiaDragon.SWEEP_MOVE -> "sweep";
			case AtroxiiaDragon.QUAKE_MOVE -> "quake";
			case AtroxiiaDragon.BITE_MOVE -> "bite";
			case AtroxiiaDragon.LASH_MOVE -> "lash";
			default -> throw new IllegalArgumentException("Unknown Atroxiia move " + move);
		};
	}

	private static PlayState move(AnimationTest<AtroxiiaDragon> test) {
		AtroxiiaDragon dragon = test.animatable();
		float speed = 1.0F;
		RawAnimation anim;
		if (dragon.crashAnim > SHOWN) {
			anim = CRASH;
		} else if (dragon.swimAnim > SHOWN) {
			anim = SWIM;
			speed = Math.max(0.6F, dragon.walkAnimation.speed() / SWIM_AT);
		} else if (dragon.sitAnim > SHOWN) {
			anim = SIT;
		} else if (test.isMoving()) {
			boolean running = dragon.walkAnimation.speed() > RUN_WHEN;
			anim = running ? RUN : WALK;
			speed = Math.max(0.4F, dragon.walkAnimation.speed() / (running ? RUN_AT : WALK_AT));
		} else {
			int cycle = (int) Math.floorDiv(dragon.tickCount + (long) IDLE_OFFSET, (long) IDLE_TICKS);
			anim = switch (Math.floorMod(cycle, 3)) {
				case 0 -> IDLE_TAUNT;
				case 1 -> IDLE_SNIFF;
				default -> IDLE_GRUMBLE;
			};
		}
		test.setControllerSpeed(Mth.clamp(speed, 0.2F, 3.0F));
		return test.setAndContinue(anim);
	}
}
