package com.jaymoskitchen.client.mixin;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.jaymoskitchen.client.ragdoll.LimpMobs;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A GeckoLib mob lying limp (see LimpMobs) is laid on its ragdoll as a whole, in place of GeckoLib's own turn (its
 * bones aren't the model parts the ragdoll bends, so its limbs stay as its animation has them).
 */
@Mixin(GeoEntityRenderer.class)
abstract class GeoLimpMobMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskitchen$limpState(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		LimpMobs.extract(entity, state, partialTicks);
	}

	@Inject(method = "applyRotations", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$limpPlace(RenderPassInfo<?> info, PoseStack poseStack, float nativeScale, CallbackInfo ci) {
		if (info.renderState() instanceof EntityRenderState state && LimpMobs.place(state, poseStack, nativeScale)) {
			ci.cancel();
		}
	}
}
