package com.jaymospowers.client.mixin;

import com.jaymospowers.client.CarryPose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player's render state is finished here (after the living part): the carry is put on it again, last. */
@Mixin(value = AvatarRenderer.class, priority = 2000)
abstract class CarryAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$carryState(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		CarryPose.extract(entity, state, partialTicks);
	}
}
