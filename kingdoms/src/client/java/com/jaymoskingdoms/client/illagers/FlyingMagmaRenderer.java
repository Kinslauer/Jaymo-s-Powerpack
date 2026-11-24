package com.jaymoskingdoms.client.illagers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.jaymoskingdoms.illagers.FlyingMagma;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Blocks;

public class FlyingMagmaRenderer extends EntityRenderer<FlyingMagma, FlyingMagmaRenderState> {
   public static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();
   private final BlockModelResolver blockModelResolver;

   public FlyingMagmaRenderer(Context context) {
      super(context);
      this.shadowRadius = 0.5F;
      this.blockModelResolver = context.getBlockModelResolver();
   }

   public FlyingMagmaRenderState createRenderState() {
      return new FlyingMagmaRenderState();
   }

   public void extractRenderState(FlyingMagma entity, FlyingMagmaRenderState state, float partialTick) {
      super.extractRenderState(entity, state, partialTick);
      this.blockModelResolver.update(state.blockModel, Blocks.MAGMA_BLOCK.defaultBlockState(), BLOCK_DISPLAY_CONTEXT);
      state.xRot = entity.getXRot(partialTick);
      state.yRot = entity.getYRot(partialTick);
   }

   public void submit(FlyingMagmaRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
      if (!state.blockModel.isEmpty()) {
         poseStack.pushPose();
         poseStack.rotate(Axis.YP.rotationDegrees(state.yRot));
         poseStack.rotate(Axis.ZP.rotationDegrees(state.xRot));
         state.blockModel.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
         poseStack.popPose();
         super.submit(state, poseStack, submitNodeCollector, camera);
      }
   }
}
