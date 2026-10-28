package com.jaymospowers.client.armory;

import com.jaymospowers.armory.HammerSlam;
import net.bettercombat.client.animation.PlayerAttackAnimatable;
import net.bettercombat.logic.AnimatedHand;
import net.fabricmc.loader.api.FabricLoader;

/**
 * (0.17.93) Your own warhammer smash plays Better Combat's two-handed slam ({@link HammerSlam}), drawn by Better Combat
 * in first and third person like its own swings. Only touches Better Combat when it's loaded.
 */
public final class HammerSlamClient {
	private HammerSlamClient() {
	}

	public static void init() {
		if (!FabricLoader.getInstance().isModLoaded("bettercombat") || !FabricLoader.getInstance().isModLoaded("player_animation_library")) {
			return;
		}
		HammerSlam.localSwing = player -> {
			if (player instanceof PlayerAttackAnimatable animatable) {
				animatable.playAttackAnimation(HammerSlam.ANIMATION, AnimatedHand.TWO_HANDED, HammerSlam.LENGTH, HammerSlam.UPSWING);
			}
		};
	}
}
