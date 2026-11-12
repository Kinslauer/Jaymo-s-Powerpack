package com.jaymospowers.client.mixin;

import com.jaymospowers.client.combat.ArmedPose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A weapon in the hand: how it's carried, and its draw, onto the render state (see ArmedPose). */
@Mixin(AvatarRenderer.class)
abstract class ArmedAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$armedState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		ArmedPose.extract(entity, state, partialTicks);
	}
}
