package com.jaymoskingdoms.client.illagers;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.illager.AbstractIllager.IllagerArmPose;

public class MarauderModel extends CustomIllagerModel<MarauderRenderState> {
   private final ModelPart rightArm;
   private final ModelPart leftArm;

   public MarauderModel(ModelPart root) {
      super(root);
      this.leftArm = root.getChild("left_arm");
      this.rightArm = root.getChild("right_arm");
   }

   public void setupAnim(MarauderRenderState renderState) {
      super.setupAnim(renderState);
      if (renderState.armPose == IllagerArmPose.ATTACKING && !renderState.getMainHandItemState().isEmpty()) {
         if (renderState.mainArm == HumanoidArm.RIGHT) {
            this.rightArm.xRot = this.rightArm.xRot - (float) (Math.PI / 4) * renderState.chargingProgress;
         } else {
            this.leftArm.xRot = this.leftArm.xRot - (float) (Math.PI / 4) * renderState.chargingProgress;
         }
      }
   }
}
