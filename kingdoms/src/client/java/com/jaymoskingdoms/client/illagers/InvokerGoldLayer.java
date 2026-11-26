package com.jaymoskingdoms.client.illagers;

import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public class InvokerGoldLayer extends EyesLayer<InvokerRenderState, IllagerModel<InvokerRenderState>> {
   private static final RenderType RENDER_TYPE = RenderTypes.eyes(JaymosKingdoms.id("textures/entity/illagers/invoker_gold.png"));

   public InvokerGoldLayer(RenderLayerParent<InvokerRenderState, IllagerModel<InvokerRenderState>> renderLayerParent) {
      super(renderLayerParent);
   }

   public RenderType renderType() {
      return RENDER_TYPE;
   }
}
