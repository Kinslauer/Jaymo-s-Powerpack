package com.jaymospowers.client.power.wind;

import com.zigythebird.playeranim.accessors.IAvatarAnimationState;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/** The Player Animation Library calls behind {@link CombatAnimCompat}; only loaded when the library is. */
final class PalAccess {
	private static final Identifier ATTACK = Identifier.fromNamespaceAndPath("bettercombat", "attack");
	/** The weapon's held pose under the attack (the longsword's guard). */
	private static final Identifier POSE = Identifier.fromNamespaceAndPath("bettercombat", "pose_main_hand_body");

	private PalAccess() {
	}

	static CombatAnimCompat.@Nullable Attack attack(Entity entity) {
		if (!(entity instanceof Avatar avatar)) {
			return null;
		}
		try {
			IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(avatar, ATTACK);
			if (!(layer instanceof AnimationController controller) || !controller.isActive()) {
				return null;
			}
			Animation animation = controller.getCurrentAnimationInstance();
			if (animation == null) {
				return null;
			}
			boolean mirrored = false;
			for (AbstractModifier modifier : controller.getModifiers()) {
				if (modifier instanceof MirrorModifier mirror && mirror.enabled) {
					mirrored = true;
				}
			}
			return new CombatAnimCompat.Attack(animation.uuid(), animation.getNameOrId(), mirrored, controller.getAnimationTicks());
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** The elbows' folds (right, left) of the attack playing, or else of the held pose; null for none or a mirrored one. */
	@SuppressWarnings("removal")
	static float @Nullable [] elbows(Entity entity) {
		if (!(entity instanceof Avatar avatar)) {
			return null;
		}
		try {
			for (Identifier id : new Identifier[] {ATTACK, POSE}) {
				IAnimation layer = PlayerAnimationAccess.getPlayerAnimationLayer(avatar, id);
				if (!(layer instanceof AnimationController controller) || !controller.isActive()) {
					continue;
				}
				for (AbstractModifier modifier : controller.getModifiers()) {
					if (modifier instanceof MirrorModifier mirror && mirror.enabled) {
						return null;
					}
				}
				PlayerAnimBone right = new PlayerAnimBone("right_arm");
				PlayerAnimBone left = new PlayerAnimBone("left_arm");
				controller.get3DTransform(right);
				controller.get3DTransform(left);
				return right.bend == 0.0F && left.bend == 0.0F ? null : new float[] {right.bend, left.bend};
			}
		} catch (RuntimeException e) {
			// the library changed: straight arms
		}
		return null;
	}

	static boolean firstPersonPass(Object state) {
		return state instanceof IAvatarAnimationState animated && animated.playerAnimLib$isFirstPersonPass();
	}
}
