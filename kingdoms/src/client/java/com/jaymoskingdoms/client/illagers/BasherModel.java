package com.jaymoskingdoms.client.illagers;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.illager.AbstractIllager.IllagerArmPose;

public class BasherModel extends CustomIllagerModel<StunnableIllagerRenderState> {
   private final ModelPart head;
   private final ModelPart rightArm;
   private final ModelPart leftArm;

   public BasherModel(ModelPart root) {
      super(root);
      this.head = root.getChild("head");
      this.leftArm = root.getChild("left_arm");
      this.rightArm = root.getChild("right_arm");
   }

   public void setupAnim(StunnableIllagerRenderState renderState) {
      super.setupAnim(renderState);
      if (renderState.armPose == IllagerArmPose.ATTACKING && renderState.shieldInMainHand && !renderState.isStunned) {
         if (renderState.mainArm == HumanoidArm.RIGHT) {
            this.rightArm.xRot *= 0.5F;
            this.rightArm.yRot = (float) (-Math.PI / 6);
         }

         if (renderState.mainArm == HumanoidArm.LEFT) {
            this.leftArm.xRot *= 0.5F;
            this.leftArm.yRot = (float) (Math.PI / 6);
         }
      }

      if (renderState.isStunned) {
         this.head.zRot = 0.3F * Mth.sin(0.45F * renderState.ageInTicks);
         this.head.xRot = 0.4F;
         this.rightArm.xRot = -0.25F;
         this.leftArm.xRot = -0.25F;
      }
   }
}
