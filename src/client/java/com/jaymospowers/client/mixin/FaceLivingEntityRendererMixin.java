package com.jaymospowers.client.mixin;

import com.jaymospowers.client.face.PlayerFaces;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (Motion patch 3) Players' faces: blinking, looking about, frowning (see PlayerFaces), drawn by swapping the skin. */
@Mixin(LivingEntityRenderer.class)
abstract class FaceLivingEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$face(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		PlayerFaces.extract(entity, state, partialTicks);
	}

	@ModifyVariable(method = "getRenderType", at = @At("STORE"), ordinal = 0)
	private Identifier jaymospowers$faceSkin(Identifier texture, LivingEntityRenderState state) {
		return PlayerFaces.texture(state, texture);
	}
}
