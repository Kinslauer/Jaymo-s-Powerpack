package com.jaymospowers.client.mixin.tacz;

import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.18.79) Andy: "bad crash just then". Every client in sight of a mannequin was kicked ("Network Protocol Error"):
 * TaCZ registers its player-animation layers with the Player Animation Library for every {@link Avatar}, and its
 * rotation layer casts the avatar to {@link Player} for a yaw modifier. A 26.3 mannequin is an Avatar but not a Player,
 * so creating one on the client threw and the packet handler dropped the connection. Mannequins are everywhere now
 * (the Practice Dummy, sparring partners and afterimages are mannequins, and vanilla lets anyone place one). For an
 * avatar that isn't a player the layer gets TaCZ's plain controller, with no modifier, as its other three layers do.
 * Targets the lambda by its compiled name, which is tied to this TaCZ build (packMods pins it). Skipped when TaCZ isn't
 * there.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.compat.playeranimator.animation.AnimationDataRegisterFactory", remap = false)
abstract class TaczMannequinAnimMixin {
	@Shadow
	private static PlayerAnimationController controller(Avatar avatar) {
		throw new AssertionError();
	}

	@Inject(method = "lambda$registerData$3", at = @At("HEAD"), cancellable = true, require = 0)
	private static void jaymospowers$notAPlayer(Avatar avatar, CallbackInfoReturnable<IAnimation> cir) {
		if (!(avatar instanceof Player)) {
			cir.setReturnValue(controller(avatar));
		}
	}
}
