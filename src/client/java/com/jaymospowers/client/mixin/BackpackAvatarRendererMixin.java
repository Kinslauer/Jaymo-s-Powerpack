package com.jaymospowers.client.mixin;

import com.jaymospowers.client.backpack.BackpackClient;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.11.89) A worn backpack's sway goes on the player's render state (BackpackClient). */
@Mixin(AvatarRenderer.class)
abstract class BackpackAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$backpack(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		BackpackClient.extract(entity, state, partialTicks);
		// (Motion patch 4) after the backpack, so a lantern pole stands clear of it
		com.jaymospowers.client.baubles.WornLanterns.extract(entity, state, partialTicks);
	}
}
