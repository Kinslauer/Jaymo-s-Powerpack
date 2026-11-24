package com.jaymoskingdoms.client.illagers;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.illagers.Surrendered;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public class SurrenderedRenderer extends HumanoidMobRenderer<Surrendered, SurrenderedRenderState, SurrenderedModel> {
   private static final Identifier TEXTURE_LOCATION = JaymosKingdoms.id("textures/entity/illagers/surrendered.png");
   private static final Identifier CHARGING_TEXTURE_LOCATION = JaymosKingdoms.id("textures/entity/illagers/surrendered_charge.png");

   public SurrenderedRenderer(Context context) {
      super(context, new SurrenderedModel(context.bakeLayer(ModModelLayers.SURRENDERED)), 0.5F);
      this.addLayer(
         new HumanoidArmorLayer(
            this, ArmorModelSet.bake(ModModelLayers.SURRENDERED_ARMOR, context.getModelSet(), SurrenderedModel::new), context.getEquipmentRenderer()
         )
      );
   }

   public Vec3 getRenderOffset(SurrenderedRenderState renderState) {
      return new Vec3(0.0, -0.35, 0.0);
   }

   public SurrenderedRenderState createRenderState() {
      return new SurrenderedRenderState();
   }

   public void extractRenderState(Surrendered entity, SurrenderedRenderState reusedState, float partialTick) {
      super.extractRenderState(entity, reusedState, partialTick);
      reusedState.isAggressive = entity.isAggressive();
      reusedState.isCharging = entity.isCharging();
   }

   public Identifier getTextureLocation(SurrenderedRenderState renderState) {
      return renderState.isCharging ? CHARGING_TEXTURE_LOCATION : TEXTURE_LOCATION;
   }
}
