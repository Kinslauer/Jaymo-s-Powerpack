package com.jaymoskingdoms.client.illagers;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.illager.AbstractIllager.IllagerArmPose;

public class InquisitorModel extends CustomIllagerModel<StunnableIllagerRenderState> {
   private final ModelPart head;
   private final ModelPart rightArm;
   private final ModelPart leftArm;

   public InquisitorModel(ModelPart root) {
      super(root);
      this.head = root.getChild("head");
      this.leftArm = root.getChild("left_arm");
      this.rightArm = root.getChild("right_arm");
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition meshDefinition = ((com.jaymoskingdoms.client.mixin.LayerDefinitionAccessor)IllagerModel.createBodyLayer()).jaymoskingdoms$mesh();
      PartDefinition partDefinition = meshDefinition.getRoot().getChild("right_arm");
      PartDefinition partDefinition1 = meshDefinition.getRoot().getChild("left_arm");
      partDefinition1.addOrReplaceChild(
         "left_shoulder_pad",
         CubeListBuilder.create().texOffs(0, 64).mirror().addBox(-0.7F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(1.1F)),
         PartPose.ZERO
      );
      partDefinition.addOrReplaceChild(
         "right_shoulder_pad", CubeListBuilder.create().texOffs(0, 64).addBox(-3.3F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(1.1F)), PartPose.ZERO
      );
      return LayerDefinition.create(meshDefinition, 64, 128);
   }

   public void setupAnim(StunnableIllagerRenderState renderState) {
      super.setupAnim(renderState);
      if (renderState.armPose == IllagerArmPose.ATTACKING) {
         if (renderState.shieldInOffHand) {
            if (renderState.mainArm == HumanoidArm.RIGHT) {
               this.leftArm.xRot = -1.05F;
               this.leftArm.yRot = (float) (Math.PI / 6);
            }

            if (renderState.mainArm == HumanoidArm.LEFT) {
               this.rightArm.xRot = 200.0F;
               this.rightArm.yRot = (float) (-Math.PI / 6);
            }
         }

         if (renderState.isStunned) {
            this.head.zRot = 0.3F * Mth.sin(0.45F * renderState.ageInTicks);
            this.head.xRot = 0.4F;
            this.rightArm.xRot = 200.0F;
            this.rightArm.yRot = (float) (Math.PI / 6);
            this.leftArm.xRot = 200.0F;
            this.leftArm.yRot = (float) (-Math.PI / 6);
         }
      }
   }
}
