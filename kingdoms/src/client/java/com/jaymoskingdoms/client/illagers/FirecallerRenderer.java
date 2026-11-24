package com.jaymoskingdoms.client.illagers;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.illagers.Firecaller;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.resources.Identifier;

public class FirecallerRenderer extends IllagerRenderer<Firecaller, IllagerRenderState> {
   private static final Identifier TEXTURE_LOCATION = JaymosKingdoms.id("textures/entity/illagers/firecaller.png");

   public FirecallerRenderer(Context context) {
      super(context, new FirecallerModel(context.bakeLayer(ModModelLayers.FIRECALLER)), 0.5F);
      ((IllagerModel)this.model).getHat().visible = true;
   }

   public IllagerRenderState createRenderState() {
      return new IllagerRenderState();
   }

   public Identifier getTextureLocation(IllagerRenderState renderState) {
      return TEXTURE_LOCATION;
   }
}
