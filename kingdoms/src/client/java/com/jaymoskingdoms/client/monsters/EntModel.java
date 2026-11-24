package com.jaymoskingdoms.client.monsters;

import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * the ent, from {@code MoCModelEnt}: a walking tree, its head turning with its neck, arms and legs swinging in turn
 * (of DrZhark's Mo' Creatures, GPL-3, see {@code licenses/mocreatures}), ported to 26.3 by {@code tools/gen_moc_small.py}:
 * its boxes and its animation as they were.
 */

final class EntModel extends EntityModel<BeastState> {
	private static final float RAD = 57.29578F;
	private final ModelPart Body, LShoulder, LArm, LWrist, LHand, LFingers, RShoulder, RArm, RWrist, RHand, RFingers, LLeg, LThigh, LKnee, LAnkle, LFoot, RLeg, RThigh, RKnee, RAnkle, RFoot, Neck, Face, Head, Nose, Mouth, TreeBase, Leave1, Leave2, Leave3, Leave4, Leave5, Leave6, Leave7, Leave8, Leave9, Leave10, Leave11, Leave12, Leave13, Leave14, Leave15, Leave16;

	EntModel(ModelPart root) {
		super(root);
		this.Body = root.getChild("Body");
		this.LShoulder = root.getChild("LShoulder");
		this.LArm = root.getChild("LArm");
		this.LWrist = root.getChild("LWrist");
		this.LHand = root.getChild("LHand");
		this.LFingers = root.getChild("LFingers");
		this.RShoulder = root.getChild("RShoulder");
		this.RArm = root.getChild("RArm");
		this.RWrist = root.getChild("RWrist");
		this.RHand = root.getChild("RHand");
		this.RFingers = root.getChild("RFingers");
		this.LLeg = root.getChild("LLeg");
		this.LThigh = root.getChild("LThigh");
		this.LKnee = root.getChild("LKnee");
		this.LAnkle = root.getChild("LAnkle");
		this.LFoot = root.getChild("LFoot");
		this.RLeg = root.getChild("RLeg");
		this.RThigh = root.getChild("RThigh");
		this.RKnee = root.getChild("RKnee");
		this.RAnkle = root.getChild("RAnkle");
		this.RFoot = root.getChild("RFoot");
		this.Neck = root.getChild("Neck");
		this.Face = root.getChild("Face");
		this.Head = root.getChild("Head");
		this.Nose = root.getChild("Nose");
		this.Mouth = root.getChild("Mouth");
		this.TreeBase = root.getChild("TreeBase");
		this.Leave1 = root.getChild("Leave1");
		this.Leave2 = root.getChild("Leave2");
		this.Leave3 = root.getChild("Leave3");
		this.Leave4 = root.getChild("Leave4");
		this.Leave5 = root.getChild("Leave5");
		this.Leave6 = root.getChild("Leave6");
		this.Leave7 = root.getChild("Leave7");
		this.Leave8 = root.getChild("Leave8");
		this.Leave9 = root.getChild("Leave9");
		this.Leave10 = root.getChild("Leave10");
		this.Leave11 = root.getChild("Leave11");
		this.Leave12 = root.getChild("Leave12");
		this.Leave13 = root.getChild("Leave13");
		this.Leave14 = root.getChild("Leave14");
		this.Leave15 = root.getChild("Leave15");
		this.Leave16 = root.getChild("Leave16");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_Body = root.addOrReplaceChild("Body", CubeListBuilder.create().texOffs(68, 36).mirror(false).addBox(-7.5F, -12.5F, -4.5F, 15F, 25F, 9F), PartPose.offset(0F, -31F, 0F));
		PartDefinition p_LShoulder = root.addOrReplaceChild("LShoulder", CubeListBuilder.create().texOffs(48, 108).mirror(false).addBox(6F, -14F, -4.8F, 9F, 7F, 7F), PartPose.offsetAndRotation(0F, -31F, 0F, 0F, 0F, -0.1745329F));
		PartDefinition p_LArm = root.addOrReplaceChild("LArm", CubeListBuilder.create().texOffs(80, 108).mirror(false).addBox(0F, -4F, -5F, 6F, 24F, 6F), PartPose.offsetAndRotation(10F, -42F, 1F, 0F, 0F, -0.1745329F));
		PartDefinition p_LWrist = root.addOrReplaceChild("LWrist", CubeListBuilder.create().texOffs(0, 169).mirror(false).addBox(2F, 17F, -6F, 8F, 15F, 8F), PartPose.offset(10F, -42F, 1F));
		PartDefinition p_LHand = root.addOrReplaceChild("LHand", CubeListBuilder.create().texOffs(88, 241).mirror(false).addBox(1F, 28F, -7F, 10F, 5F, 10F), PartPose.offset(10F, -42F, 1F));
		PartDefinition p_LFingers = root.addOrReplaceChild("LFingers", CubeListBuilder.create().texOffs(88, 176).mirror(false).addBox(1F, 33F, -7F, 10F, 15F, 10F), PartPose.offset(10F, -42F, 1F));
		PartDefinition p_RShoulder = root.addOrReplaceChild("RShoulder", CubeListBuilder.create().texOffs(48, 122).mirror(false).addBox(-15F, -14F, -4.8F, 9F, 7F, 7F), PartPose.offsetAndRotation(0F, -31F, 0F, 0F, 0F, 0.1745329F));
		PartDefinition p_RArm = root.addOrReplaceChild("RArm", CubeListBuilder.create().texOffs(104, 108).mirror(false).addBox(-6F, -4F, -5F, 6F, 24F, 6F), PartPose.offsetAndRotation(-10F, -42F, 1F, 0F, 0F, 0.1745329F));
		PartDefinition p_RWrist = root.addOrReplaceChild("RWrist", CubeListBuilder.create().texOffs(32, 169).mirror(false).addBox(-10F, 17F, -6F, 8F, 15F, 8F), PartPose.offset(-10F, -42F, 1F));
		PartDefinition p_RHand = root.addOrReplaceChild("RHand", CubeListBuilder.create().texOffs(88, 226).mirror(false).addBox(-11F, 28F, -7F, 10F, 5F, 10F), PartPose.offset(-10F, -42F, 1F));
		PartDefinition p_RFingers = root.addOrReplaceChild("RFingers", CubeListBuilder.create().texOffs(88, 201).mirror(false).addBox(-11F, 33F, -7F, 10F, 15F, 10F), PartPose.offset(-10F, -42F, 1F));
		PartDefinition p_LLeg = root.addOrReplaceChild("LLeg", CubeListBuilder.create().texOffs(0, 90).mirror(false).addBox(3F, 0F, -3F, 6F, 20F, 6F), PartPose.offset(0F, -21F, 0F));
		PartDefinition p_LThigh = root.addOrReplaceChild("LThigh", CubeListBuilder.create().texOffs(24, 64).mirror(false).addBox(2.5F, 4F, -3.5F, 7F, 12F, 7F), PartPose.offset(0F, -21F, 0F));
		PartDefinition p_LKnee = root.addOrReplaceChild("LKnee", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(2F, 20F, -4F, 8F, 24F, 8F), PartPose.offset(0F, -21F, 0F));
		PartDefinition p_LAnkle = root.addOrReplaceChild("LAnkle", CubeListBuilder.create().texOffs(32, 29).mirror(false).addBox(1.5F, 25F, -4.5F, 9F, 20F, 9F), PartPose.offset(0F, -21F, 0F));
		PartDefinition p_LFoot = root.addOrReplaceChild("LFoot", CubeListBuilder.create().texOffs(0, 206).mirror(false).addBox(1.5F, 38F, -23.5F, 9F, 5F, 9F), PartPose.offsetAndRotation(0F, -21F, 0F, 0.2617994F, 0F, 0F));
		PartDefinition p_RLeg = root.addOrReplaceChild("RLeg", CubeListBuilder.create().texOffs(0, 64).mirror(false).addBox(-9F, 0F, -3F, 6F, 20F, 6F), PartPose.offset(0F, -21F, 0F));
		PartDefinition p_RThigh = root.addOrReplaceChild("RThigh", CubeListBuilder.create().texOffs(24, 83).mirror(false).addBox(-9.5F, 4F, -3.5F, 7F, 12F, 7F), PartPose.offset(0F, -21F, 0F));
		PartDefinition p_RKnee = root.addOrReplaceChild("RKnee", CubeListBuilder.create().texOffs(0, 32).mirror(false).addBox(-10F, 20F, -4F, 8F, 24F, 8F), PartPose.offset(0F, -21F, 0F));
		PartDefinition p_RAnkle = root.addOrReplaceChild("RAnkle", CubeListBuilder.create().texOffs(32, 0).mirror(false).addBox(-10.5F, 25F, -4.5F, 9F, 20F, 9F), PartPose.offset(0F, -21F, 0F));
		PartDefinition p_RFoot = root.addOrReplaceChild("RFoot", CubeListBuilder.create().texOffs(0, 192).mirror(false).addBox(-10.5F, 38F, -23.5F, 9F, 5F, 9F), PartPose.offsetAndRotation(0F, -21F, 0F, 0.2617994F, 0F, 0F));
		PartDefinition p_Neck = root.addOrReplaceChild("Neck", CubeListBuilder.create().texOffs(52, 90).mirror(false).addBox(-4F, -8F, -5.8F, 8F, 10F, 8F), PartPose.offsetAndRotation(0F, -44F, 0F, 0.5235988F, 0F, 0F));
		PartDefinition p_Face = root.addOrReplaceChild("Face", CubeListBuilder.create().texOffs(52, 70).mirror(false).addBox(-4.5F, -11F, -9F, 9F, 7F, 8F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Head = root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(84, 88).mirror(false).addBox(-6F, -20.5F, -9.5F, 12F, 10F, 10F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Nose = root.addOrReplaceChild("Nose", CubeListBuilder.create().texOffs(82, 88).mirror(false).addBox(-1.5F, -12F, -12F, 3F, 7F, 3F), PartPose.offsetAndRotation(0F, -44F, 0F, -0.122173F, 0F, 0F));
		PartDefinition p_Mouth = root.addOrReplaceChild("Mouth", CubeListBuilder.create().texOffs(77, 36).mirror(false).addBox(-3F, -8F, -6.8F, 6F, 2F, 1F), PartPose.offsetAndRotation(0F, -44F, 0F, 0.5235988F, 0F, 0F));
		PartDefinition p_TreeBase = root.addOrReplaceChild("TreeBase", CubeListBuilder.create().texOffs(0, 136).mirror(false).addBox(-10F, -31.5F, -11.5F, 20F, 13F, 20F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave1 = root.addOrReplaceChild("Leave1", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(-16F, -45F, -17F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave2 = root.addOrReplaceChild("Leave2", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(0F, -45F, -17F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave3 = root.addOrReplaceChild("Leave3", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(0F, -45F, -1F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave4 = root.addOrReplaceChild("Leave4", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(-16F, -45F, -1F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave5 = root.addOrReplaceChild("Leave5", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(-16F, -45F, -33F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave6 = root.addOrReplaceChild("Leave6", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(0F, -45F, -33F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave7 = root.addOrReplaceChild("Leave7", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(16F, -45F, -17F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave8 = root.addOrReplaceChild("Leave8", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(16F, -45F, -1F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave9 = root.addOrReplaceChild("Leave9", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(0F, -45F, 15F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave10 = root.addOrReplaceChild("Leave10", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(-16F, -45F, 15F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave11 = root.addOrReplaceChild("Leave11", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(-32F, -45F, -1F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave12 = root.addOrReplaceChild("Leave12", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(-32F, -45F, -17F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave13 = root.addOrReplaceChild("Leave13", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(-16F, -61F, -17F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave14 = root.addOrReplaceChild("Leave14", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(0F, -61F, -17F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave15 = root.addOrReplaceChild("Leave15", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(0F, -61F, -1F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		PartDefinition p_Leave16 = root.addOrReplaceChild("Leave16", CubeListBuilder.create().texOffs(0, 224).mirror(false).addBox(-16F, -61F, -1F, 16F, 16F, 16F), PartPose.offset(0F, -44F, 0F));
		return LayerDefinition.create(mesh, 128, 256);
	}

	@Override
	public void setupAnim(BeastState state) {
		super.setupAnim(state);
		rig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
	}

	private void rig(float f, float f1, float f2, float f3, float f4) {

        //super.setRotationAngles(f, f1, f2, f3, f4, 0.0625F);
        float RArmXRot = Mth.cos(f * 0.6662F + (float) Math.PI) * 2.0F * f1 * 0.5F;
        float LArmXRot = Mth.cos(f * 0.6662F) * 2.0F * f1 * 0.5F;
        float RLegXRot = Mth.cos(f * 0.6662F) * 1.0F * f1;
        float LLegXRot = Mth.cos(f * 0.6662F + (float) Math.PI) * 1.0F * f1;

        this.LWrist.zRot = (Mth.cos(f2 * 0.09F) * 0.05F) - 0.05F;
        this.LWrist.xRot = LArmXRot;
        this.RWrist.zRot = -(Mth.cos(f2 * 0.09F) * 0.05F) + 0.05F;
        this.RWrist.xRot = RArmXRot;

        this.LHand.xRot = this.LFingers.xRot = this.LArm.xRot = this.LWrist.xRot;
        this.LHand.zRot = this.LFingers.zRot = this.LWrist.zRot;
        this.LArm.zRot = (-10F / RAD) + this.LWrist.zRot;

        this.RHand.xRot = this.RFingers.xRot = this.RArm.xRot = this.RWrist.xRot;
        this.RHand.zRot = this.RFingers.zRot = this.RWrist.zRot;
        this.RArm.zRot = (10F / RAD) + this.RWrist.zRot;

        this.RLeg.xRot = RLegXRot;
        this.LLeg.xRot = LLegXRot;
        this.LThigh.xRot = this.LKnee.xRot = this.LAnkle.xRot = this.LLeg.xRot;
        this.RThigh.xRot = this.RKnee.xRot = this.RAnkle.xRot = this.RLeg.xRot;

        this.LFoot.xRot = (15F / RAD) + this.LLeg.xRot;
        this.RFoot.xRot = (15F / RAD) + this.RLeg.xRot;
        this.Neck.yRot = f3 / RAD; //this moves head to left and right
        //Neck.rotateAngleX = f4/ RAD;

        this.Mouth.yRot =
                this.Face.yRot = this.Nose.yRot = this.Head.yRot = this.TreeBase.yRot = this.Neck.yRot;
        this.Leave1.yRot =
                this.Leave2.yRot =
                        this.Leave3.yRot =
                                this.Leave4.yRot = this.Leave5.yRot = this.Leave6.yRot = this.Neck.yRot;
        this.Leave7.yRot =
                this.Leave8.yRot = this.Leave9.yRot = this.Leave10.yRot = this.Leave11.yRot = this.Neck.yRot;
        this.Leave12.yRot =
                this.Leave13.yRot =
                        this.Leave14.yRot = this.Leave15.yRot = this.Leave16.yRot = this.Neck.yRot;

    
	}
}
