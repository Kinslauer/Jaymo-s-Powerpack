package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.client.HungerPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The curse of the hungry on a player's limbs: on all fours the arms reach down and the head lifts (see {@link HungerPose}). */
@Mixin(PlayerModel.class)
abstract class HungerPoseModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void jaymoskingdoms$hungerLimbs(AvatarRenderState state, CallbackInfo ci) {
		HungerPose.pose((HumanoidModel<?>)(Object)this, state);
	}
}
