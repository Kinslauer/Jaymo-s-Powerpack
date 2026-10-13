package com.jaymospowers.client.mixin;

import com.jaymospowers.client.HiddenArmorClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player's hidden armour pieces are taken off their render state last, after every power has dressed it. */
@Mixin(value = AvatarRenderer.class, priority = 2100)
abstract class HiddenArmorAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$hideArmor(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		HiddenArmorClient.extract(entity, state);
	}
}
