package com.jaymospowers.client.armory.guns;

import com.geckolib.animatable.GeoAnimatable;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.util.GeckoLibUtil;

/**
 * One of the flintlock guns as GeckoLib animates it: one per gun model, with an animation state for each hand holding
 * one (the renderer's instance id). Two controllers: {@code state} holds the gun loaded, spent, aimed or reloading, and
 * plays the draw when it comes into the hand; {@code shot} plays the fire (hammer, flash, kick) over it when a shot
 * lands, and keys only those bones, so whatever the state holds carries on underneath.
 */
final class GunAnimatable implements GeoAnimatable {
	static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
	static final RawAnimation SPENT = RawAnimation.begin().thenLoop("spent");
	static final RawAnimation AIM = RawAnimation.begin().thenLoop("aim");
	static final RawAnimation RELOAD = RawAnimation.begin().thenLoop("reload");
	static final RawAnimation DRAW = RawAnimation.begin().thenPlay("draw");
	static final RawAnimation FIRE = RawAnimation.begin().thenPlay("fire");
	/** Ticks the state controller takes to blend from one held pose to the next. */
	static final int BLEND_TICKS = 3;

	/** Per hand, kept between frames: the shot last played, and a draw waiting to start. */
	private static final DataTicket<Long> PLAYED_SHOT = DataTicket.create("jaymospowers_gun_played_shot", Long.class);
	private static final DataTicket<Long> PLAYED_DRAW = DataTicket.create("jaymospowers_gun_played_draw", Long.class);

	final String name;
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	GunAnimatable(String name) {
		this.name = name;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>("state", BLEND_TICKS, this::state));
		controllers.add(new AnimationController<GunAnimatable>("shot", 0, this::shot));
	}

	private PlayState state(AnimationTest<GunAnimatable> test) {
		long draw = test.getDataOrDefault(GunRenderer.DRAW_START, -1L);
		Long played = test.manager().getAnimatableData(PLAYED_DRAW);
		if (draw >= 0 && (played == null || played != draw)) {
			test.manager().setAnimatableData(PLAYED_DRAW, draw);
			test.controller().reset();
			return test.setAndContinue(DRAW);
		}
		if (test.isCurrentAnimation(DRAW) && !test.controller().hasAnimationFinished()) {
			return PlayState.CONTINUE;
		}
		GunState.Pose pose = test.getDataOrDefault(GunRenderer.POSE, GunState.Pose.LOADED);
		if (pose == GunState.Pose.RELOAD) {
			return test.setAndContinue(RELOAD);
		}
		if (test.getDataOrDefault(GunRenderer.AIMING, false)) {
			return test.setAndContinue(AIM);
		}
		return test.setAndContinue(pose == GunState.Pose.SPENT ? SPENT : IDLE);
	}

	private PlayState shot(AnimationTest<GunAnimatable> test) {
		long shot = test.getDataOrDefault(GunRenderer.SHOT_START, -1L);
		Long played = test.manager().getAnimatableData(PLAYED_SHOT);
		if (shot >= 0 && (played == null || played != shot)) {
			test.manager().setAnimatableData(PLAYED_SHOT, shot);
			test.controller().reset();
			return test.setAndContinue(FIRE);
		}
		if (test.isCurrentAnimation(FIRE) && !test.controller().hasAnimationFinished()) {
			return PlayState.CONTINUE;
		}
		return PlayState.STOP;
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}
}
