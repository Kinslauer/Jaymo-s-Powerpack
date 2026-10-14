package com.jaymospowers.client.power.wind;

import com.zigythebird.playeranim.accessors.IAvatarAnimationState;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/** The Player Animation Library calls behind {@link CombatAnimCompat}; only loaded when the library is. */
final class PalAccess {
	private static final Identifier ATTACK = Identifier.fromNamespaceAndPath("bettercombat", "attack");

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

	static boolean firstPersonPass(Object state) {
		return state instanceof IAvatarAnimationState animated && animated.playerAnimLib$isFirstPersonPass();
	}
}
