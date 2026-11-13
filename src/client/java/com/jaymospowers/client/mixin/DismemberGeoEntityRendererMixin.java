package com.jaymospowers.client.mixin;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.jaymospowers.client.Dismembered;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lost parts on GeckoLib mobs (a Kingdoms Imp): GeckoLib's entity renderer isn't a LivingEntityRenderer, so the body's
 * lost parts are put on its render state here (as DismemberLivingEntityRendererMixin does for the game's), and the bones
 * for them are hidden in each render pass ({@link Dismembered#hideGeo}), after the renderer's own bone changes.
 */
@Mixin(value = GeoEntityRenderer.class, remap = false)
abstract class DismemberGeoEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$lostParts(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		if (entity instanceof LivingEntity living) {
			Dismembered.extract(living, state);
		}
	}

	@Inject(method = "adjustRenderPose(Lcom/geckolib/renderer/base/RenderPassInfo;)V", at = @At("TAIL"))
	private void jaymospowers$hideLost(RenderPassInfo<?> info, CallbackInfo ci) {
		Dismembered.hideGeo(info);
	}
}
