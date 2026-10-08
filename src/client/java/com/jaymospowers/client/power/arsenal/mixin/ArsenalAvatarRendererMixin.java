package com.jaymospowers.client.power.arsenal.mixin;

import com.jaymospowers.client.power.arsenal.ArsenalArmPose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts the Reach Through arm pose on a player's render state (see ArsenalArmPose). */
@Mixin(AvatarRenderer.class)
abstract class ArsenalAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$arsenalReachArm(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		ArsenalArmPose.extract(entity, state, partialTicks);
	}
}
