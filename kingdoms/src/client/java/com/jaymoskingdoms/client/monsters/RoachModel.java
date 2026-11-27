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
 * the roach, from {@code MoCModelRoach}: its shell closed on the ground, open with wings beating in the air, feelers and legs working
 * (of DrZhark's Mo' Creatures, GPL-3, see {@code licenses/mocreatures}), ported to 26.3 by {@code tools/gen_moc_small.py}:
 * its boxes and its animation as they were.
 */

final class RoachModel extends EntityModel<BeastState> {
	private static final float RAD = 57.29578F;
	private final ModelPart Head, LAnthenna, LAnthennaB, RAnthenna, RAnthennaB, Thorax, FrontLegs, MidLegs, RearLegs, Abdomen, TailL, TailR, LShellClosed, RShellClosed, LShellOpen, RShellOpen, LeftWing, RightWing;
	private boolean isFlying;

	RoachModel(ModelPart root) {
		super(root);
		this.Head = root.getChild("Head");
		this.LAnthenna = root.getChild("Head").getChild("LAnthenna");
		this.LAnthennaB = root.getChild("Head").getChild("LAnthenna").getChild("LAnthennaB");
		this.RAnthenna = root.getChild("Head").getChild("RAnthenna");
		this.RAnthennaB = root.getChild("Head").getChild("RAnthenna").getChild("RAnthennaB");
		this.Thorax = root.getChild("Thorax");
		this.FrontLegs = root.getChild("FrontLegs");
		this.MidLegs = root.getChild("MidLegs");
		this.RearLegs = root.getChild("RearLegs");
		this.Abdomen = root.getChild("Abdomen");
		this.TailL = root.getChild("TailL");
		this.TailR = root.getChild("TailR");
		this.LShellClosed = root.getChild("LShellClosed");
		this.RShellClosed = root.getChild("RShellClosed");
		this.LShellOpen = root.getChild("LShellOpen");
		this.RShellOpen = root.getChild("RShellOpen");
		this.LeftWing = root.getChild("LeftWing");
		this.RightWing = root.getChild("RightWing");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_Head = root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-0.5F, 0F, -1F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, 23F, -2F, -2.171231F, 0F, 0F));
		PartDefinition p_LAnthenna = p_Head.addOrReplaceChild("LAnthenna", CubeListBuilder.create().texOffs(3, 21).mirror(false).addBox(0F, 0F, 0F, 4F, 0F, 1F), PartPose.offsetAndRotation(0.5F, 0F, 0F, -1.5707963F, 0.4363323F, 0F));
		PartDefinition p_LAnthennaB = p_LAnthenna.addOrReplaceChild("LAnthennaB", CubeListBuilder.create().texOffs(4, 21).mirror(false).addBox(0F, 0F, 1F, 3F, 0F, 1F), PartPose.offsetAndRotation(2.5F, 0F, -0.5F, 0F, 0.7853982F, 0F));
		PartDefinition p_RAnthenna = p_Head.addOrReplaceChild("RAnthenna", CubeListBuilder.create().texOffs(3, 19).mirror(false).addBox(-4.5F, 0F, 0F, 4F, 0F, 1F), PartPose.offsetAndRotation(0F, 0F, 0F, -1.5707963F, -0.4363323F, 0F));
		PartDefinition p_RAnthennaB = p_RAnthenna.addOrReplaceChild("RAnthennaB", CubeListBuilder.create().texOffs(4, 19).mirror(false).addBox(-4F, 0F, 1F, 3F, 0F, 1F), PartPose.offsetAndRotation(-2.5F, 0F, 0.5F, 0F, -0.7853982F, 0F));
		PartDefinition p_Thorax = root.addOrReplaceChild("Thorax", CubeListBuilder.create().texOffs(0, 3).mirror(false).addBox(-1F, 0F, -1F, 2F, 1F, 2F), PartPose.offset(0F, 22F, -1F));
		PartDefinition p_FrontLegs = root.addOrReplaceChild("FrontLegs", CubeListBuilder.create().texOffs(0, 11).mirror(false).addBox(-2F, 0F, 0F, 4F, 2F, 0F), PartPose.offsetAndRotation(0F, 23F, -1.8F, -1.115358F, 0F, 0F));
		PartDefinition p_MidLegs = root.addOrReplaceChild("MidLegs", CubeListBuilder.create().texOffs(0, 13).mirror(false).addBox(-2.5F, 0F, 0F, 5F, 2F, 0F), PartPose.offsetAndRotation(0F, 23F, -1.2F, 1.264073F, 0F, 0F));
		PartDefinition p_RearLegs = root.addOrReplaceChild("RearLegs", CubeListBuilder.create().texOffs(0, 15).mirror(false).addBox(-2F, 0F, 0F, 4F, 4F, 0F), PartPose.offsetAndRotation(0F, 23F, -0.4F, 1.368173F, 0F, 0F));
		PartDefinition p_Abdomen = root.addOrReplaceChild("Abdomen", CubeListBuilder.create().texOffs(0, 6).mirror(false).addBox(-1F, 0F, -1F, 2F, 4F, 1F), PartPose.offsetAndRotation(0F, 22F, 0F, 1.427659F, 0F, 0F));
		PartDefinition p_TailL = root.addOrReplaceChild("TailL", CubeListBuilder.create().texOffs(2, 29).mirror(false).addBox(-0.5F, 0F, 0F, 1F, 2F, 0F), PartPose.offsetAndRotation(0F, 23F, 3.6F, 1.554066F, 0.6457718F, 0F));
		PartDefinition p_TailR = root.addOrReplaceChild("TailR", CubeListBuilder.create().texOffs(0, 29).mirror(false).addBox(-0.5F, 0F, 0F, 1F, 2F, 0F), PartPose.offsetAndRotation(0F, 23F, 3.6F, 1.554066F, -0.6457718F, 0F));
		PartDefinition p_LShellClosed = root.addOrReplaceChild("LShellClosed", CubeListBuilder.create().texOffs(4, 23).mirror(false).addBox(0F, 0F, 0F, 2F, 0F, 6F), PartPose.offsetAndRotation(0F, 21.5F, -1.5F, -0.1487144F, -0.0872665F, 0.1919862F));
		PartDefinition p_RShellClosed = root.addOrReplaceChild("RShellClosed", CubeListBuilder.create().texOffs(0, 23).mirror(false).addBox(-2F, 0F, 0F, 2F, 0F, 6F), PartPose.offsetAndRotation(0F, 21.5F, -1.5F, -0.1487144F, 0.0872665F, -0.1919862F));
		PartDefinition p_LShellOpen = root.addOrReplaceChild("LShellOpen", CubeListBuilder.create().texOffs(4, 23).mirror(false).addBox(0F, 0F, 0F, 2F, 0F, 6F), PartPose.offsetAndRotation(0F, 21.5F, -1.5F, 1.117011F, -0.0872665F, 1.047198F));
		PartDefinition p_RShellOpen = root.addOrReplaceChild("RShellOpen", CubeListBuilder.create().texOffs(0, 23).mirror(false).addBox(-2F, 0F, 0F, 2F, 0F, 6F), PartPose.offsetAndRotation(0F, 21.5F, -1.5F, 1.117011F, 0.0872665F, -1.047198F));
		PartDefinition p_LeftWing = root.addOrReplaceChild("LeftWing", CubeListBuilder.create().texOffs(11, 21).mirror(false).addBox(0F, 1F, -1F, 6F, 0F, 2F), PartPose.offsetAndRotation(0F, 21.5F, -1.5F, 0F, -1.047198F, -0.4363323F));
		PartDefinition p_RightWing = root.addOrReplaceChild("RightWing", CubeListBuilder.create().texOffs(11, 19).mirror(false).addBox(-6F, 1F, -1F, 6F, 0F, 2F), PartPose.offsetAndRotation(0F, 21.5F, -1.5F, 0F, 1.047198F, 0.4363323F));
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(BeastState state) {
		super.setupAnim(state);
		this.isFlying = state.airborne;
		for (ModelPart p : List.of(LShellOpen, RShellOpen, LeftWing, RightWing)) {
			p.visible = this.isFlying;
		}
		for (ModelPart p : List.of(LShellClosed, RShellClosed)) {
			p.visible = !this.isFlying;
		}
		rig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
	}

	private void rig(float f, float f1, float f2, float f3, float f4) {

        this.Head.xRot = -2.171231F + (f4 / 57.29578F);
        //Head.rotateAngleY = f3 / 57.29578F;

        //f1 = movement speed!
        //f2 = timer!

        float antMov = 5F / RAD + (f1 * 1.5F);

        this.LAnthenna.zRot = -antMov;
        this.RAnthenna.zRot = antMov;

        float legMov = 0F;
        float legMovB = 0F;

        float frontLegAdj = 0F;

        if (this.isFlying) {
            float WingRot = Mth.cos((f2 * 2.0F)) * 0.7F;
            this.RightWing.yRot = 1.047198F + WingRot;
            this.LeftWing.yRot = -1.047198F - WingRot;
            legMov = (f1 * 1.5F);
            legMovB = legMov;
            frontLegAdj = 1.4F;

        } else {
            legMov = Mth.cos((f * 1.5F) + 3.141593F) * 0.6F * f1;
            legMovB = Mth.cos(f * 1.5F) * 0.8F * f1;
        }

        //AntennaB.rotateAngleX = 2.88506F - legMov;

        this.FrontLegs.xRot = -1.115358F + frontLegAdj + legMov;
        this.MidLegs.xRot = 1.264073F + legMovB;
        this.RearLegs.xRot = 1.368173F - frontLegAdj + legMov;

    
	}
}
