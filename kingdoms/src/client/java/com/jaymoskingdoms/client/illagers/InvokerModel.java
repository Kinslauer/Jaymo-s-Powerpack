package com.jaymoskingdoms.client.illagers;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.illager.IllagerModel;

public class InvokerModel extends IllagerModel<InvokerRenderState> {
   private final ModelPart body;
   private final ModelPart leftLeg;
   private final ModelPart rightLeg;

   public InvokerModel(ModelPart root) {
      super(root);
      this.body = root.getChild("body");
      this.leftLeg = root.getChild("left_leg");
      this.rightLeg = root.getChild("right_leg");
   }

   public static LayerDefinition createBodyLayer(CubeDeformation cubeDeformation) {
      MeshDefinition meshDefinition = ((com.jaymoskingdoms.client.mixin.LayerDefinitionAccessor)robedBodyLayer(cubeDeformation)).jaymoskingdoms$mesh();
      PartDefinition partDefinition = meshDefinition.getRoot();
      partDefinition.addOrReplaceChild(
         "body",
         CubeListBuilder.create()
            .texOffs(16, 20)
            .addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F, cubeDeformation)
            .texOffs(0, 38)
            .addBox(-4.0F, 0.0F, -3.0F, 8.0F, 22.0F, 6.0F, cubeDeformation.extend(0.5F)),
         PartPose.ZERO
      );
      return LayerDefinition.create(meshDefinition, 128, 128);
   }

   public void setupAnim(InvokerRenderState renderState) {
      super.setupAnim(renderState);
      this.body.xRot = this.rightLeg.xRot = this.leftLeg.xRot = 0.15F * renderState.floatAnimationSpeed;
   }

   /** The necromancer's robed body (Illager Invasion), which the invoker's is built on. */
   static LayerDefinition robedBodyLayer(CubeDeformation cubeDeformation) {
      MeshDefinition meshDefinition = new MeshDefinition();
      PartDefinition partDefinition = meshDefinition.getRoot();
      PartDefinition partDefinition2 = partDefinition.addOrReplaceChild(
         "head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 10.0F, 8.0F, cubeDeformation), PartPose.ZERO
      );
      partDefinition2.addOrReplaceChild(
         "hat", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 12.0F, 8.0F, cubeDeformation.extend(0.45F)), PartPose.ZERO
      );
      partDefinition2.addOrReplaceChild(
         "nose", CubeListBuilder.create().texOffs(24, 0).addBox(-1.0F, -1.0F, -6.0F, 2.0F, 4.0F, 2.0F, cubeDeformation), PartPose.offset(0.0F, -2.0F, 0.0F)
      );
      partDefinition.addOrReplaceChild(
         "body",
         CubeListBuilder.create()
            .texOffs(16, 20)
            .addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F, cubeDeformation)
            .texOffs(0, 38)
            .addBox(-4.0F, 0.0F, -3.0F, 8.0F, 20.0F, 6.0F, cubeDeformation.extend(0.5F)),
         PartPose.ZERO
      );
      PartDefinition partDefinition3 = partDefinition.addOrReplaceChild(
         "arms",
         CubeListBuilder.create()
            .texOffs(44, 22)
            .addBox(-8.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, cubeDeformation)
            .texOffs(40, 38)
            .addBox(-4.0F, 2.0F, -2.0F, 8.0F, 4.0F, 4.0F, cubeDeformation),
         PartPose.offsetAndRotation(0.0F, 3.0F, -1.0F, -0.75F, 0.0F, 0.0F)
      );
      partDefinition3.addOrReplaceChild(
         "left_shoulder", CubeListBuilder.create().texOffs(44, 22).mirror().addBox(4.0F, -2.0F, -2.0F, 4.0F, 8.0F, 4.0F, cubeDeformation), PartPose.ZERO
      );
      partDefinition.addOrReplaceChild(
         "right_leg",
         CubeListBuilder.create().texOffs(0, 22).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation),
         PartPose.offset(-2.0F, 12.0F, 0.0F)
      );
      partDefinition.addOrReplaceChild(
         "left_leg",
         CubeListBuilder.create().texOffs(0, 22).mirror().addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation),
         PartPose.offset(2.0F, 12.0F, 0.0F)
      );
      partDefinition.addOrReplaceChild(
         "right_arm",
         CubeListBuilder.create().texOffs(40, 46).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation),
         PartPose.offset(-5.0F, 2.0F, 0.0F)
      );
      partDefinition.addOrReplaceChild(
         "left_arm",
         CubeListBuilder.create().texOffs(40, 46).mirror().addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, cubeDeformation),
         PartPose.offset(5.0F, 2.0F, 0.0F)
      );
      return LayerDefinition.create(meshDefinition, 64, 64);
   }
}
