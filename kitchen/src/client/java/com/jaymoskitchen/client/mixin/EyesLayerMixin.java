package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.client.corpse.ClosedEyes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.5.19) A corpse's glowing eyes (a spider's, an enderman's) go out: see ClosedEyes. */
@Mixin(EyesLayer.class)
abstract class EyesLayerMixin {
	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$deadEyes(PoseStack poseStack, SubmitNodeCollector collector, int light, EntityRenderState state,
		float yRot, float xRot, CallbackInfo ci) {
		if (ClosedEyes.closed(state)) {
			ci.cancel();
		}
	}
}
