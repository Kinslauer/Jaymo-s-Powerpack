package com.jaymospowers.client.power.web.mixin;

import com.jaymospowers.client.power.web.WebSymbioteClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Spider-Sense: whatever is about to hurt an ascended web slinger is outlined red on their screen. */
@Mixin(EntityRenderer.class)
abstract class WebEntityRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$webSpiderSense(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		com.jaymospowers.client.power.web.WebBehemothClient.extract(entity, state);
		int color = WebSymbioteClient.senseColor(entity);
		if (color != 0) {
			state.outlineColor = color;
		}
	}
}
