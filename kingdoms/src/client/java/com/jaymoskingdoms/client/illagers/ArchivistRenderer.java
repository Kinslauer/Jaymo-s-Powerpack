package com.jaymoskingdoms.client.illagers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.illagers.Archivist;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.model.object.book.BookModel.State;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.EnchantTableRenderer;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class ArchivistRenderer extends IllagerRenderer<Archivist, ArchivistRenderState> {
   private static final Identifier TEXTURE_LOCATION = JaymosKingdoms.id("textures/entity/illagers/archivist.png");
   public static final State DEFAULT_BOOK_STATE = new State(0.0F, 0.0F, 0.0F);
   private final SpriteGetter sprites;

   public ArchivistRenderer(final Context context) {
      super(context, new CustomIllagerModel(context.bakeLayer(ModModelLayers.ARCHIVIST)), 0.5F);
      this.sprites = context.getSprites();
      this.addLayer(
         new ItemInHandLayer<ArchivistRenderState, IllagerModel<ArchivistRenderState>>(this) {
            private final BookModel book = new BookModel(context.bakeLayer(ModModelLayers.ARCHIVIST_BOOK));

            public void submit(
               PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight, ArchivistRenderState renderState, float yRot, float xRot
            ) {
               if (renderState.isCastingSpell) {
                  super.submit(poseStack, nodeCollector, packedLight, renderState, yRot, xRot);
               }

               poseStack.pushPose();
               poseStack.translate(0.0F, 0.362F, -0.5F);
               poseStack.rotate(Axis.YP.rotationDegrees(270.0F));
               if (renderState.isCastingSpell) {
                  poseStack.translate(-0.4F, 0.0F, 0.0F);
                  poseStack.rotate(Axis.ZN.rotationDegrees(30.0F));
               }

               nodeCollector.submitModel(
                  this.book,
                  renderState.book,
                  poseStack,
                  renderState.lightCoords,
                  OverlayTexture.NO_OVERLAY,
                  -1,
                  EnchantTableRenderer.BOOK_TEXTURE,
                  ArchivistRenderer.this.sprites,
                  renderState.outlineColor
               );
               poseStack.popPose();
            }
         }
      );
      ((IllagerModel)this.model).getHat().visible = true;
   }

   public ArchivistRenderState createRenderState() {
      return new ArchivistRenderState();
   }

   public void extractRenderState(Archivist entity, ArchivistRenderState renderState, float partialTick) {
      super.extractRenderState(entity, renderState, partialTick);
      renderState.isCastingSpell = entity.isCastingSpell();
      if (renderState.isCastingSpell) {
         renderState.book = State.forAnimation(0.0F, 10.0F + Mth.cos(renderState.ageInTicks) * 0.55F, 0.0F, 1.05F);
      } else {
         renderState.book = DEFAULT_BOOK_STATE;
      }
   }

   public Identifier getTextureLocation(ArchivistRenderState renderState) {
      return TEXTURE_LOCATION;
   }
}
