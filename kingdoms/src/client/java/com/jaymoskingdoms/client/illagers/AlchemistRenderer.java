package com.jaymoskingdoms.client.illagers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.illagers.Alchemist;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.resources.Identifier;

public class AlchemistRenderer extends IllagerRenderer<Alchemist, IllagerRenderState> {
   private static final Identifier TEXTURE_LOCATION = JaymosKingdoms.id("textures/entity/illagers/alchemist.png");

   public AlchemistRenderer(Context context) {
      super(context, new CustomIllagerModel(context.bakeLayer(ModModelLayers.ALCHEMIST)), 0.5F);
      this.addLayer(new ItemInHandLayer<IllagerRenderState, IllagerModel<IllagerRenderState>>(this) {
         public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, IllagerRenderState renderState, float yRot, float xRot) {
            if (renderState.isAggressive) {
               super.submit(poseStack, nodeCollector, packedLight, renderState, yRot, xRot);
            }
         }
      });
      ((IllagerModel)this.model).getHat().visible = true;
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition meshDefinition = ((com.jaymoskingdoms.client.mixin.LayerDefinitionAccessor)IllagerModel.createBodyLayer()).jaymoskingdoms$mesh();
      PartDefinition partDefinition = meshDefinition.getRoot().getChild("head").getChild("hat");
      partDefinition.addOrReplaceChild(
         "brim",
         CubeListBuilder.create().texOffs(69, 47).addBox(-8.0F, -8.0F, -6.0F, 16.0F, 16.0F, 1.0F),
         PartPose.rotation((float) (-Math.PI / 2), 0.0F, 0.0F)
      );
      return LayerDefinition.create(meshDefinition, 128, 64);
   }

   public IllagerRenderState createRenderState() {
      return new IllagerRenderState();
   }

   public Identifier getTextureLocation(IllagerRenderState renderState) {
      return TEXTURE_LOCATION;
   }
}
