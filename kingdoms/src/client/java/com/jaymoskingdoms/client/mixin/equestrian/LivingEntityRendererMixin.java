package com.jaymoskingdoms.client.mixin.equestrian;

import com.jaymoskingdoms.client.equestrian.HorseLooks;
import com.jaymoskingdoms.client.equestrian.TackLayer;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.5.34) A horse drawn in its breed's build: a draft broad, a courser slim and long-legged; (0.5.38) kneeling. */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
	/** How far a kneeling horse goes down (blocks) and tips forward (degrees). */
	private static final float KNEEL_DROP = 0.35F, KNEEL_TILT = 14.0F;

	@Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
	private void jaymoskingdoms$build(LivingEntityRenderState state, PoseStack poseStack, CallbackInfo ci) {
		float[] build = state.getData(HorseLooks.BUILD);
		if (build != null) {
			poseStack.scale(build[0], build[1], build[2]);
		}
		// (0.5.38) kneeling for an armoured rider: down at the front
		TackLayer.Look look = state.getData(TackLayer.LOOK);
		if (look != null && look.kneel() > 0.0F) {
			poseStack.translate(0.0F, -KNEEL_DROP * look.kneel(), 0.0F);
			poseStack.rotate(Axis.XP.rotationDegrees(KNEEL_TILT * look.kneel()));
		}
	}
}
