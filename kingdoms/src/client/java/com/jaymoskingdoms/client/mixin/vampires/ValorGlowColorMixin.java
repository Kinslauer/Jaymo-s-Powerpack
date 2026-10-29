package com.jaymoskingdoms.client.mixin.vampires;

import com.jaymoskingdoms.client.BloodVision;
import com.jaymoskingdoms.client.ValorClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.360) Valor's Senses colour their outlines (a body glowing for any other reason, or for blood vision, keeps its own). */
@Mixin(EntityRenderer.class)
abstract class ValorGlowColorMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
	private void jaymoskingdoms$valorColor(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (state.outlineColor != 0 && !entity.isCurrentlyGlowing() && !BloodVision.sensed(entity) && ValorClient.sensed(entity)) {
			state.outlineColor = ValorClient.color(entity, partialTicks);
		}
	}
}
