package com.jaymoskingdoms.client.illagers;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.illagers.Marauder;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.Identifier;

public class MarauderRenderer extends IllagerRenderer<Marauder, MarauderRenderState> {
   private static final Identifier TEXTURE_LOCATION = JaymosKingdoms.id("textures/entity/illagers/marauder.png");

   public MarauderRenderer(Context context) {
      super(context, new MarauderModel(context.bakeLayer(ModModelLayers.MARAUDER)), 0.5F);
      this.addLayer(new ItemInHandLayer(this));
   }

   public MarauderRenderState createRenderState() {
      return new MarauderRenderState();
   }

   public void extractRenderState(Marauder entity, MarauderRenderState reusedState, float partialTick) {
      super.extractRenderState(entity, reusedState, partialTick);
      reusedState.chargingProgress = entity.getChargingProgress(partialTick);
   }

   public Identifier getTextureLocation(MarauderRenderState renderState) {
      return TEXTURE_LOCATION;
   }
}
