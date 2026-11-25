package com.jaymoskingdoms.client.illagers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.illagers.Basher;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.Identifier;

public class BasherRenderer extends IllagerRenderer<Basher, StunnableIllagerRenderState> {
   private static final Identifier TEXTURE_LOCATION = JaymosKingdoms.id("textures/entity/illagers/basher.png");

   public BasherRenderer(Context context) {
      super(context, new BasherModel(context.bakeLayer(ModModelLayers.BASHER)), 0.5F);
      this.addLayer(
         new ItemInHandLayer<StunnableIllagerRenderState, IllagerModel<StunnableIllagerRenderState>>(this) {
            public void submit(
               PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, StunnableIllagerRenderState renderState, float yRot, float xRot
            ) {
               if (renderState.isAggressive) {
                  super.submit(poseStack, nodeCollector, packedLight, renderState, yRot, xRot);
               }
            }
         }
      );
   }

   public StunnableIllagerRenderState createRenderState() {
      return new StunnableIllagerRenderState();
   }

   public void extractRenderState(Basher entity, StunnableIllagerRenderState reusedState, float partialTick) {
      super.extractRenderState(entity, reusedState, partialTick);
      reusedState.extractRenderState(entity);
   }

   public Identifier getTextureLocation(StunnableIllagerRenderState renderState) {
      return TEXTURE_LOCATION;
   }
}
