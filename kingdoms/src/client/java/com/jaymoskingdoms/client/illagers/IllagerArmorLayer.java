package com.jaymoskingdoms.client.illagers;

import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.util.Mth;

public abstract class IllagerArmorLayer<S extends IllagerRenderState & PowerableRenderState, M extends IllagerModel<S>> extends EnergySwirlLayer<S, M> {
   private final M model;

   public IllagerArmorLayer(RenderLayerParent<S, M> renderer, M model) {
      super(renderer);
      this.model = model;
   }

   protected boolean isPowered(S renderState) {
      return renderState.isPowered();
   }

   protected float xOffset(float tickCount) {
      return Mth.cos(tickCount * 0.2F) * 0.2F;
   }

   protected M model() {
      return this.model;
   }
}
