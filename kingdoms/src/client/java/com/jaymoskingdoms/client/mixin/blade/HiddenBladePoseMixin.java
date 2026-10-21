package com.jaymoskingdoms.client.mixin.blade;

import com.jaymoskingdoms.client.HiddenBladeClient;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.117) A player with a hidden blade sprung holds that arm up ahead in a guard, and thrusts with it (see HiddenBladeClient). */
@Mixin(PlayerModel.class)
abstract class HiddenBladePoseMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void jaymoskingdoms$bladeGuard(AvatarRenderState state, CallbackInfo ci) {
		HiddenBladeClient.pose((PlayerModel)(Object)this, state);
	}
}
