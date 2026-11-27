package com.jaymospowers.client.compat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.freerun.CoreMotion;
import com.jaymospowers.freerun.Slide;
import com.jaymospowers.power.web.WebPose;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.stamina.Dodge;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.SpeedModifier;
import com.zigythebird.playeranimcore.enums.PlayState;
import java.util.Objects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** PAL playback for shared moves and katana forms. Existing movement state drives the clips on every client. */
public final class CoreMotionPal {
	private static final Identifier LAYER = JaymosPowers.id("core_motion");
	private static final int PRIORITY = 1050;

	private record Move(Identifier id, String group, long start, int duration, boolean mirror) {
	}

	private static final class Layer extends PlayerAnimationController {
		final SpeedModifier speed = new SpeedModifier(1.0F);
		final MirrorModifier mirror = new MirrorModifier();
		@Nullable Move shown;
		/** Game time this slide began (seen on this client), or -1 when not sliding. */
		long slideSince = -1;

		Layer(Avatar avatar) {
			super(avatar, (controller, state, setter) -> PlayState.STOP);
			addModifierLast(speed);
			addModifierLast(mirror);
		}
	}

	private CoreMotionPal() {
	}

	public static void init() {
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, PRIORITY, Layer::new);
		ClientTickEvents.END_CLIENT_TICK.register(CoreMotionPal::tick);
	}

	public static boolean playing(Player player, String group) {
		return player instanceof AbstractClientPlayer avatar
			&& PlayerAnimationAccess.getPlayerAnimationLayer(avatar, LAYER) instanceof Layer layer
			&& layer.shown != null && group.equals(layer.shown.group());
	}

	public static boolean active(Player player) {
		return player instanceof AbstractClientPlayer avatar
			&& PlayerAnimationAccess.getPlayerAnimationLayer(avatar, LAYER) instanceof Layer layer
			&& layer.shown != null && !shortLanding(layer.shown);
	}

	/** The light and medium landing clips (small drops) are a quick knee bend: held weapons and body weight stay on over them. */
	private static boolean shortLanding(Move move) {
		String path = move.id().getPath();
		return path.endsWith("landingposev1") || path.endsWith("landingposev2");
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.level == null) {
			return;
		}
		for (AbstractClientPlayer player : minecraft.level.players()) {
			if (!(PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof Layer layer)) {
				continue;
			}
			boolean sliding = Slide.sliding(player) && !Slide.belly(player);
			if (!sliding) {
				layer.slideSince = -1;
			} else if (layer.slideSince < 0) {
				layer.slideSince = player.level().getGameTime();
			}
			Move move = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson() ? null : move(player, layer);
			if (Objects.equals(layer.shown, move)) {
				continue;
			}
			layer.stopTriggeredAnimation();
			layer.stop();
			layer.shown = null;
			if (move != null && PlayerAnimResources.hasAnimation(move.id())) {
				var animation = PlayerAnimResources.getAnimation(move.id());
				layer.speed.speed = move.duration() > 0 ? animation.length() / move.duration() : 1.0F;
				layer.mirror.enabled = move.mirror();
				layer.forceAnimationReset();
				layer.triggerAnimation(animation);
				layer.shown = move;
			}
		}
	}

	private static @Nullable Move move(Player player, Layer layer) {
		if (!player.isAlive() || player.isPassenger() || player.isSwimming() || player.isFallFlying()
			|| WebPower.ragdolled(player)) {
			return null;
		}
		long now = player.level().getGameTime();
		CoreMotion.Clip clip = player.getAttached(CoreMotion.CLIP);
		if (clip != null && now < clip.until() && clip.animation().getPath().startsWith("katana_form.")
			&& com.jaymospowers.combat.KatanaForms.isKatana(player.getMainHandItem())) {
			return new Move(clip.animation(), "katana", clip.until(), 0, player.getMainArm() == HumanoidArm.LEFT);
		}
		WebPose pose = player.getAttached(WebPower.POSE);
		if (pose != null && now - pose.start() < pose.ticks()) {
			String flip = switch (pose.kind()) {
				case WebPose.FRONT_FLIP -> "frontflip";
				case WebPose.BACK_FLIP, WebPose.STAND_BACKFLIP -> "backflip";
				default -> null;
			};
			if (flip != null && (!player.onGround() || pose.kind() == WebPose.STAND_BACKFLIP)) {
				return core(flip, "flip", pose.start(), pose.ticks(), false);
			}
		}
		Dodge.Rolling roll = player.getAttached(Dodge.ROLLING);
		if (roll != null && !roll.flip() && !Boolean.TRUE.equals(player.getAttached(Dodge.STRETCHY))) {
			int ticks = roll.dash() ? Dodge.DASH_TICKS : roll.extended() ? Dodge.LONG_ROLL_TICKS : Dodge.ROLL_TICKS;
			if (now - roll.start() < ticks) {
				Vec3 forward = Vec3.directionFromRotation(0.0F, player.getYRot());
				double along = forward.x * roll.x() + forward.z * roll.z();
				double side = forward.z * roll.x() - forward.x * roll.z();
				String dodge = along > 0.7 ? "evade_one" : along < -0.7 ? "evade_two" : "evade_three";
				return core(roll.dash() ? dodge : roll.extended() ? "longroll" : "roll", "dodge", roll.start(), ticks, side > 0);
			}
		}
		// One clip per slide; a long slide (downhill, water skips) hands back to RunPose's own slide pose after it.
		if (layer.slideSince >= 0 && now - layer.slideSince < Slide.TICKS) {
			return core("sprintslide", "slide", layer.slideSince, Slide.TICKS, false);
		}
		if (clip != null && now < clip.until() && clip.animation().getPath().startsWith("animation.player.")) {
			boolean landing = clip.animation().getPath().startsWith("animation.player.landingpose");
			if (landing == player.onGround()) {
				return new Move(clip.animation(), "motion", clip.until(), 0, false);
			}
		}
		return null;
	}

	private static Move core(String clip, String group, long start, int ticks, boolean mirror) {
		return new Move(JaymosPowers.id("animation.player." + clip), group, start, ticks, mirror);
	}
}
