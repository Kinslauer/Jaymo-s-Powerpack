package com.jaymoskingdoms.client.illagers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.illagers.Provoker;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.Identifier;

public class ProvokerRenderer extends IllagerRenderer<Provoker, SpellcasterIllagerRenderState> {
   private static final Identifier TEXTURE_LOCATION = JaymosKingdoms.id("textures/entity/illagers/provoker.png");

   public ProvokerRenderer(Context context) {
      super(context, new CustomIllagerModel(context.bakeLayer(ModModelLayers.PROVOKER)), 0.5F);
      this.addLayer(
         new ItemInHandLayer<SpellcasterIllagerRenderState, IllagerModel<SpellcasterIllagerRenderState>>(this) {
            public void submit(
               PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, SpellcasterIllagerRenderState renderState, float yRot, float xRot
            ) {
               if (renderState.isAggressive || renderState.isCastingSpell) {
                  super.submit(poseStack, nodeCollector, packedLight, renderState, yRot, xRot);
               }
            }
         }
      );
      ((IllagerModel)this.model).getHat().visible = true;
   }

   public SpellcasterIllagerRenderState createRenderState() {
      return new SpellcasterIllagerRenderState();
   }

   public void extractRenderState(Provoker entity, SpellcasterIllagerRenderState reusedState, float partialTick) {
      super.extractRenderState(entity, reusedState, partialTick);
      reusedState.isCastingSpell = entity.isCastingSpell();
   }

   public Identifier getTextureLocation(SpellcasterIllagerRenderState renderState) {
      return TEXTURE_LOCATION;
   }
}
