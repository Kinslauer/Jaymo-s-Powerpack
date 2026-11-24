package com.jaymoskingdoms.client.illagers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.illagers.Invoker;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

public class InvokerRenderer extends IllagerRenderer<Invoker, InvokerRenderState> {
   private static final Identifier TEXTURE_LOCATION = JaymosKingdoms.id("textures/entity/illagers/invoker.png");
   private static final Identifier ARMOR_TEXTURE_LOCATION = Identifier.withDefaultNamespace("textures/entity/wither/wither_armor.png");

   public InvokerRenderer(Context context) {
      super(context, new InvokerModel(context.bakeLayer(ModModelLayers.INVOKER)), 0.0F);
      this.addLayer(new InvokerGoldLayer(this));
      this.addLayer(
         new IllagerArmorLayer<InvokerRenderState, IllagerModel<InvokerRenderState>>(this, new InvokerModel(context.bakeLayer(ModModelLayers.INVOKER_ARMOR))) {
            protected Identifier getTextureLocation() {
               return InvokerRenderer.ARMOR_TEXTURE_LOCATION;
            }
         }
      );
      ((IllagerModel)this.model).getHat().visible = true;
   }

   public InvokerRenderState createRenderState() {
      return new InvokerRenderState();
   }

   protected void setupRotations(InvokerRenderState renderState, PoseStack poseStack, float bodyRot, float scale) {
      super.setupRotations(renderState, poseStack, bodyRot, scale);
      float rotation = Mth.sin(renderState.ageInTicks * 0.1F) * (float) (Math.PI / 180.0);
      poseStack.rotate(new Quaternionf().rotationZYX(rotation, 0.0F, -rotation));
      poseStack.translate(0.0F, 0.3F + rotation, 0.0F);
   }

   public void extractRenderState(Invoker entity, InvokerRenderState reusedState, float partialTick) {
      super.extractRenderState(entity, reusedState, partialTick);
      reusedState.isPowered = entity.isPowered();
      if (!reusedState.isRiding) {
         reusedState.floatAnimationSpeed = reusedState.walkAnimationSpeed;
         reusedState.walkAnimationSpeed = reusedState.walkAnimationPos = 0.0F;
      }
   }

   public Identifier getTextureLocation(InvokerRenderState renderState) {
      return TEXTURE_LOCATION;
   }
}
