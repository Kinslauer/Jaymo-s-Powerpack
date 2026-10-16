package com.jaymospowers.client.rig.mixin;

import com.jaymospowers.client.rig.RigSuit;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.11.101) The worn Rig goes on the player's render state after hidden armour, costumes and forms (this priority over
 * their 2100) have taken what they hide off it; its pieces come off the state there and are drawn as the suit (RigSuit).
 */
@Mixin(value = AvatarRenderer.class, priority = 2200)
abstract class RigSuitAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$rigSuit(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		RigSuit.extract(entity, state, partialTicks);
	}
}
