package com.jaymoskingdoms.client.illagers;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.illagers.Inquisitor;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.Identifier;

public class InquisitorRenderer extends IllagerRenderer<Inquisitor, StunnableIllagerRenderState> {
   private static final Identifier TEXTURE_LOCATION = JaymosKingdoms.id("textures/entity/illagers/inquisitor.png");

   public InquisitorRenderer(Context context) {
      super(context, new InquisitorModel(context.bakeLayer(ModModelLayers.INQUISITOR)), 0.5F);
      this.addLayer(new ItemInHandLayer(this));
   }

   public StunnableIllagerRenderState createRenderState() {
      return new StunnableIllagerRenderState();
   }

   public void extractRenderState(Inquisitor entity, StunnableIllagerRenderState reusedState, float partialTick) {
      super.extractRenderState(entity, reusedState, partialTick);
      reusedState.extractRenderState(entity);
   }

   public Identifier getTextureLocation(StunnableIllagerRenderState renderState) {
      return TEXTURE_LOCATION;
   }
}
