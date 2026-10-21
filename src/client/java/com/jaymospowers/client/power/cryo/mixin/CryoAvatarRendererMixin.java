package com.jaymospowers.client.power.cryo.mixin;

import com.jaymospowers.client.power.cryo.CryoPose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.15.32) The Cryokinesis poses go on the player's render state (CryoPose). */
@Mixin(AvatarRenderer.class)
abstract class CryoAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$cryoPose(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		CryoPose.extract(entity, state, partialTicks);
	}
}
