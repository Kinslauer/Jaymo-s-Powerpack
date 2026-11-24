package com.jaymoskingdoms.client.illagers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EvokerFangsRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.state.EvokerFangsRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

public class InvokerFangsRenderer extends EvokerFangsRenderer {
   private static final Identifier TEXTURE_LOCATION = JaymosKingdoms.id("textures/entity/illagers/invoker_fangs.png");
   private final InvokerFangsModel model;

   public InvokerFangsRenderer(Context context) {
      super(context);
      this.model = new InvokerFangsModel(context.bakeLayer(ModModelLayers.INVOKER_FANGS));
   }

   public void submit(EvokerFangsRenderState renderState, PoseStack poseStack, SubmitNodeCollector nodeCollector, CameraRenderState cameraRenderState) {
      if (renderState.biteProgress != 0.0F) {
         poseStack.pushPose();
         poseStack.scale(1.15F, 1.15F, 1.15F);
         poseStack.rotate(Axis.YP.rotationDegrees(90.0F - renderState.yRot));
         float scale = 2.0F * (renderState.biteProgress > 0.9F ? (1.0F - renderState.biteProgress) / 0.1F : 1.0F);
         poseStack.scale(-scale, -scale, scale);
         poseStack.translate(0.0F, -0.626F, 0.0F);
         poseStack.scale(0.5F, 0.5F, 0.5F);
         nodeCollector.submitModel(
            this.model,
            renderState,
            poseStack,
            this.model.renderType(TEXTURE_LOCATION),
            renderState.lightCoords,
            OverlayTexture.NO_OVERLAY,
            renderState.outlineColor
         );
         poseStack.popPose();
         super.submit(renderState, poseStack, nodeCollector, cameraRenderState);
      }
   }
}
