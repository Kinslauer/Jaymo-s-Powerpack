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
 * the cricket, from {@code MoCModelCricket}: its long hind legs folded on the ground, stretched in the air with the wings beating
 * (of DrZhark's Mo' Creatures, GPL-3, see {@code licenses/mocreatures}), ported to 26.3 by {@code tools/gen_moc_small.py}:
 * its boxes and its animation as they were.
 */

final class CricketModel extends EntityModel<BeastState> {
	private static final float RAD = 57.29578F;
	private final ModelPart Head, Antenna, AntennaB, Thorax, Abdomen, TailA, TailB, FrontLegs, MidLegs, ThighLeft, ThighLeftB, ThighRight, ThighRightB, LegLeft, LegLeftB, LegRight, LegRightB, LeftWing, RightWing, FoldedWings;
	private boolean isFlying;

	CricketModel(ModelPart root) {
		super(root);
		this.Head = root.getChild("Head");
		this.Antenna = root.getChild("Antenna");
		this.AntennaB = root.getChild("AntennaB");
		this.Thorax = root.getChild("Thorax");
		this.Abdomen = root.getChild("Abdomen");
		this.TailA = root.getChild("TailA");
		this.TailB = root.getChild("TailB");
		this.FrontLegs = root.getChild("FrontLegs");
		this.MidLegs = root.getChild("MidLegs");
		this.ThighLeft = root.getChild("ThighLeft");
		this.ThighLeftB = root.getChild("ThighLeftB");
		this.ThighRight = root.getChild("ThighRight");
		this.ThighRightB = root.getChild("ThighRightB");
		this.LegLeft = root.getChild("LegLeft");
		this.LegLeftB = root.getChild("LegLeftB");
		this.LegRight = root.getChild("LegRight");
		this.LegRightB = root.getChild("LegRightB");
		this.LeftWing = root.getChild("LeftWing");
		this.RightWing = root.getChild("RightWing");
		this.FoldedWings = root.getChild("FoldedWings");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_Head = root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 4).mirror(false).addBox(-0.5F, 0F, -1F, 1F, 1F, 2F), PartPose.offsetAndRotation(0F, 22.5F, -2F, -2.171231F, 0F, 0F));
		PartDefinition p_Antenna = root.addOrReplaceChild("Antenna", CubeListBuilder.create().texOffs(0, 11).mirror(false).addBox(-1F, 0F, 0F, 2F, 2F, 0F), PartPose.offsetAndRotation(0F, 22.5F, -3F, -2.736346F, 0F, 0F));
		PartDefinition p_AntennaB = root.addOrReplaceChild("AntennaB", CubeListBuilder.create().texOffs(0, 9).mirror(false).addBox(-1F, 0F, 0F, 2F, 2F, 0F), PartPose.offsetAndRotation(0F, 20.7F, -3.8F, 2.88506F, 0F, 0F));
		PartDefinition p_Thorax = root.addOrReplaceChild("Thorax", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offset(0F, 21F, -1F));
		PartDefinition p_Abdomen = root.addOrReplaceChild("Abdomen", CubeListBuilder.create().texOffs(8, 0).mirror(false).addBox(-1F, 0F, -1F, 2F, 3F, 2F), PartPose.offsetAndRotation(0F, 22F, 0F, 1.427659F, 0F, 0F));
		PartDefinition p_TailA = root.addOrReplaceChild("TailA", CubeListBuilder.create().texOffs(4, 9).mirror(false).addBox(-1F, 0F, 0F, 2F, 3F, 0F), PartPose.offsetAndRotation(0F, 22F, 2.8F, 1.308687F, 0F, 0F));
		PartDefinition p_TailB = root.addOrReplaceChild("TailB", CubeListBuilder.create().texOffs(4, 7).mirror(false).addBox(-1F, 0F, 0F, 2F, 2F, 0F), PartPose.offsetAndRotation(0F, 23F, 2.8F, 1.665602F, 0F, 0F));
		PartDefinition p_FrontLegs = root.addOrReplaceChild("FrontLegs", CubeListBuilder.create().texOffs(0, 7).mirror(false).addBox(-1F, 0F, 0F, 2F, 2F, 0F), PartPose.offsetAndRotation(0F, 23F, -1.8F, -0.8328009F, 0F, 0F));
		PartDefinition p_MidLegs = root.addOrReplaceChild("MidLegs", CubeListBuilder.create().texOffs(0, 13).mirror(false).addBox(-2F, 0F, 0F, 4F, 2F, 0F), PartPose.offsetAndRotation(0F, 23F, -1.2F, 1.070744F, 0F, 0F));
		PartDefinition p_ThighLeft = root.addOrReplaceChild("ThighLeft", CubeListBuilder.create().texOffs(8, 5).mirror(false).addBox(0F, -3F, 0F, 1F, 3F, 1F), PartPose.offsetAndRotation(0.5F, 23F, 0F, -0.4886922F, 0.2617994F, 0F));
		PartDefinition p_ThighLeftB = root.addOrReplaceChild("ThighLeftB", CubeListBuilder.create().texOffs(8, 5).mirror(false).addBox(0F, -3F, 0F, 1F, 3F, 1F), PartPose.offsetAndRotation(0.5F, 22.5F, 0F, -1.762782F, 0F, 0F));
		PartDefinition p_ThighRight = root.addOrReplaceChild("ThighRight", CubeListBuilder.create().texOffs(12, 5).mirror(false).addBox(-1F, -3F, 0F, 1F, 3F, 1F), PartPose.offsetAndRotation(-0.5F, 23F, 0F, -0.4886922F, -0.2617994F, 0F));
		PartDefinition p_ThighRightB = root.addOrReplaceChild("ThighRightB", CubeListBuilder.create().texOffs(12, 5).mirror(false).addBox(-1F, -3F, 0F, 1F, 3F, 1F), PartPose.offsetAndRotation(-0.5F, 22.5F, 0F, -1.762782F, 0F, 0F));
		PartDefinition p_LegLeft = root.addOrReplaceChild("LegLeft", CubeListBuilder.create().texOffs(0, 15).mirror(false).addBox(0F, 0F, -1F, 0F, 3F, 2F), PartPose.offset(2F, 21F, 2.5F));
		PartDefinition p_LegLeftB = root.addOrReplaceChild("LegLeftB", CubeListBuilder.create().texOffs(4, 15).mirror(false).addBox(0F, 0F, -1F, 0F, 3F, 2F), PartPose.offsetAndRotation(1.5F, 23F, 2.9F, 1.249201F, 0F, 0F));
		PartDefinition p_LegRight = root.addOrReplaceChild("LegRight", CubeListBuilder.create().texOffs(4, 15).mirror(false).addBox(0F, 0F, -1F, 0F, 3F, 2F), PartPose.offset(-2F, 21F, 2.5F));
		PartDefinition p_LegRightB = root.addOrReplaceChild("LegRightB", CubeListBuilder.create().texOffs(4, 15).mirror(false).addBox(0F, 0F, -1F, 0F, 3F, 2F), PartPose.offsetAndRotation(-1.5F, 23F, 2.9F, 1.249201F, 0F, 0F));
		PartDefinition p_LeftWing = root.addOrReplaceChild("LeftWing", CubeListBuilder.create().texOffs(0, 30).mirror(false).addBox(0F, 0F, -1F, 6F, 0F, 2F), PartPose.offsetAndRotation(0F, 20.9F, -1F, 0F, -0.1919862F, 0F));
		PartDefinition p_RightWing = root.addOrReplaceChild("RightWing", CubeListBuilder.create().texOffs(0, 28).mirror(false).addBox(-6F, 0F, -1F, 6F, 0F, 2F), PartPose.offsetAndRotation(0F, 20.9F, -1F, 0F, 0.1919862F, 0F));
		PartDefinition p_FoldedWings = root.addOrReplaceChild("FoldedWings", CubeListBuilder.create().texOffs(0, 26).mirror(false).addBox(0F, 0F, -1F, 6F, 0F, 2F), PartPose.offsetAndRotation(0F, 20.9F, -2F, 0F, -1.570796F, 0F));
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(BeastState state) {
		super.setupAnim(state);
		this.isFlying = state.airborne;
		for (ModelPart p : List.of(ThighLeftB, ThighRightB, LegLeftB, LegRightB, LeftWing, RightWing)) {
			p.visible = this.isFlying;
		}
		for (ModelPart p : List.of(ThighLeft, ThighRight, LegLeft, LegRight, FoldedWings)) {
			p.visible = !this.isFlying;
		}
		rig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
	}

	private void rig(float f, float f1, float f2, float f3, float f4) {


        float legMov = 0F;
        float legMovB = 0F;

        float frontLegAdj = 0F;

        if (this.isFlying) {
            float WingRot = Mth.cos((f2 * 2.0F)) * 0.7F;
            this.RightWing.zRot = WingRot;
            this.LeftWing.zRot = -WingRot;
            legMov = (f1 * 1.5F);
            legMovB = legMov;
            frontLegAdj = 1.4F;

        } else {
            legMov = Mth.cos((f * 1.5F) + 3.141593F) * 2.0F * f1;
            legMovB = Mth.cos(f * 1.5F) * 2.0F * f1;
        }

        this.AntennaB.xRot = 2.88506F - legMov;

        this.FrontLegs.xRot = -0.8328009F + frontLegAdj + legMov;
        this.MidLegs.xRot = 1.070744F + legMovB;

    
	}
}
