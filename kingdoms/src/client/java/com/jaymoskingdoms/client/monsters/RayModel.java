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
 * the manta ray and the stingray, from {@code MoCModelRay}: wings rippling in a wave, the stingray's tail raised to sting
 * (of DrZhark's Mo' Creatures, GPL-3, see {@code licenses/mocreatures}), ported to 26.3 by {@code tools/gen_moc_small.py}:
 * its boxes and its animation as they were.
 */

final class RayModel extends EntityModel<BeastState> {
	private static final float RAD = 57.29578F;
	private final ModelPart Body, Right, Left, BodyU, Tail, BodyTail, RWingA, RWingB, RWingC, RWingD, RWingE, RWingF, RWingG, LWingA, LWingB, LWingC, LWingD, LWingE, LWingF, LWingG, LEye, REye;
	private boolean attacking;

	RayModel(ModelPart root) {
		super(root);
		this.Body = root.getChild("Body");
		this.Right = root.getChild("Right");
		this.Left = root.getChild("Left");
		this.BodyU = root.getChild("BodyU");
		this.Tail = root.getChild("Tail");
		this.BodyTail = root.getChild("BodyTail");
		this.RWingA = root.getChild("RWingA");
		this.RWingB = root.getChild("RWingB");
		this.RWingC = root.getChild("RWingC");
		this.RWingD = root.getChild("RWingD");
		this.RWingE = root.getChild("RWingE");
		this.RWingF = root.getChild("RWingF");
		this.RWingG = root.getChild("RWingG");
		this.LWingA = root.getChild("LWingA");
		this.LWingB = root.getChild("LWingB");
		this.LWingC = root.getChild("LWingC");
		this.LWingD = root.getChild("LWingD");
		this.LWingE = root.getChild("LWingE");
		this.LWingF = root.getChild("LWingF");
		this.LWingG = root.getChild("LWingG");
		this.LEye = root.getChild("LEye");
		this.REye = root.getChild("REye");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_Body = root.addOrReplaceChild("Body", CubeListBuilder.create().texOffs(26, 0).mirror(false).addBox(-4F, -1F, 0F, 8F, 2F, 11F), PartPose.offset(0F, 22F, -5F));
		PartDefinition p_Right = root.addOrReplaceChild("Right", CubeListBuilder.create().texOffs(10, 26).mirror(false).addBox(-0.5F, -1F, -4F, 1F, 2F, 4F), PartPose.offset(-3F, 22F, -4.8F));
		PartDefinition p_Left = root.addOrReplaceChild("Left", CubeListBuilder.create().texOffs(0, 26).mirror(false).addBox(-0.5F, -1F, -4F, 1F, 2F, 4F), PartPose.offset(3F, 22F, -4.8F));
		PartDefinition p_BodyU = root.addOrReplaceChild("BodyU", CubeListBuilder.create().texOffs(0, 11).mirror(false).addBox(-3F, -1F, 0F, 6F, 1F, 8F), PartPose.offset(0F, 21F, -4F));
		PartDefinition p_Tail = root.addOrReplaceChild("Tail", CubeListBuilder.create().texOffs(30, 15).mirror(false).addBox(-0.5F, -0.5F, 1F, 1F, 1F, 16F), PartPose.offset(0F, 22F, 8F));
		PartDefinition p_BodyTail = root.addOrReplaceChild("BodyTail", CubeListBuilder.create().texOffs(0, 20).mirror(false).addBox(-1.8F, -0.5F, -3.2F, 5F, 1F, 5F), PartPose.offsetAndRotation(0F, 22F, 7F, 0F, 1F, 0F));
		PartDefinition p_RWingA = root.addOrReplaceChild("RWingA", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-3F, -0.5F, -5F, 3F, 1F, 10F), PartPose.offset(-4F, 22F, 1F));
		PartDefinition p_RWingB = root.addOrReplaceChild("RWingB", CubeListBuilder.create().texOffs(2, 2).mirror(false).addBox(-6F, -0.5F, -4F, 3F, 1F, 8F), PartPose.offset(-4F, 22F, 1F));
		PartDefinition p_RWingC = root.addOrReplaceChild("RWingC", CubeListBuilder.create().texOffs(5, 4).mirror(false).addBox(-8F, -0.5F, -3F, 2F, 1F, 6F), PartPose.offset(-4F, 22F, 1F));
		PartDefinition p_RWingD = root.addOrReplaceChild("RWingD", CubeListBuilder.create().texOffs(6, 5).mirror(false).addBox(-10F, -0.5F, -2.5F, 2F, 1F, 5F), PartPose.offset(-4F, 22F, 1F));
		PartDefinition p_RWingE = root.addOrReplaceChild("RWingE", CubeListBuilder.create().texOffs(7, 6).mirror(false).addBox(-12F, -0.5F, -2F, 2F, 1F, 4F), PartPose.offset(-4F, 22F, 1F));
		PartDefinition p_RWingF = root.addOrReplaceChild("RWingF", CubeListBuilder.create().texOffs(8, 7).mirror(false).addBox(-14F, -0.5F, -1.5F, 2F, 1F, 3F), PartPose.offset(-4F, 22F, 1F));
		PartDefinition p_RWingG = root.addOrReplaceChild("RWingG", CubeListBuilder.create().texOffs(9, 8).mirror(false).addBox(-16F, -0.5F, -1F, 2F, 1F, 2F), PartPose.offset(-4F, 22F, 1F));
		PartDefinition p_LWingA = root.addOrReplaceChild("LWingA", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(0F, -0.5F, -5F, 3F, 1F, 10F), PartPose.offset(4F, 22F, 1F));
		PartDefinition p_LWingB = root.addOrReplaceChild("LWingB", CubeListBuilder.create().texOffs(2, 2).mirror(false).addBox(3F, -0.5F, -4F, 3F, 1F, 8F), PartPose.offset(4F, 22F, 1F));
		PartDefinition p_LWingC = root.addOrReplaceChild("LWingC", CubeListBuilder.create().texOffs(5, 4).mirror(false).addBox(6F, -0.5F, -3F, 2F, 1F, 6F), PartPose.offset(4F, 22F, 1F));
		PartDefinition p_LWingD = root.addOrReplaceChild("LWingD", CubeListBuilder.create().texOffs(6, 5).mirror(false).addBox(8F, -0.5F, -2.5F, 2F, 1F, 5F), PartPose.offset(4F, 22F, 1F));
		PartDefinition p_LWingE = root.addOrReplaceChild("LWingE", CubeListBuilder.create().texOffs(7, 6).mirror(false).addBox(10F, -0.5F, -2F, 2F, 1F, 4F), PartPose.offset(4F, 22F, 1F));
		PartDefinition p_LWingF = root.addOrReplaceChild("LWingF", CubeListBuilder.create().texOffs(8, 7).mirror(false).addBox(12F, -0.5F, -1.5F, 2F, 1F, 3F), PartPose.offset(4F, 22F, 1F));
		PartDefinition p_LWingG = root.addOrReplaceChild("LWingG", CubeListBuilder.create().texOffs(9, 8).mirror(false).addBox(14F, -0.5F, -1F, 2F, 1F, 2F), PartPose.offset(4F, 22F, 1F));
		PartDefinition p_LEye = root.addOrReplaceChild("LEye", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-3F, -2F, 1F, 1F, 1F, 2F), PartPose.offset(0F, 21F, -4F));
		PartDefinition p_REye = root.addOrReplaceChild("REye", CubeListBuilder.create().texOffs(0, 3).mirror(false).addBox(2F, -2F, 1F, 1F, 1F, 2F), PartPose.offset(0F, 21F, -4F));
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(BeastState state) {
		super.setupAnim(state);
		this.attacking = state.stinging;
		for (ModelPart p : List.of(Right, Left, RWingC, LWingC, RWingD, RWingE, RWingF, RWingG, LWingD, LWingE, LWingF, LWingG)) {
			p.visible = state.manta;
		}
		for (ModelPart p : List.of(REye, LEye)) {
			p.visible = !state.manta;
		}
		rig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
	}

	private void rig(float f, float f1, float f2, float f3, float f4) {

        //super.setRotationAngles(f, f1, f2, f3, f4, 0.0625F);
        float rotF = Mth.cos(f * 0.6662F) * 1.5F * f1;
        float f6 = 20F;
        this.Tail.yRot = Mth.cos(f * 0.6662F) * 0.7F * f1;
        this.RWingA.zRot = rotF;
        this.LWingA.zRot = -rotF;
        rotF += (rotF / f6);
        this.RWingB.zRot = rotF;
        this.LWingB.zRot = -rotF;
        rotF += (rotF / f6);

        this.RWingC.zRot = rotF;
        this.LWingC.zRot = -rotF;
        rotF += (rotF / f6);

        this.RWingD.zRot = rotF;
        this.LWingD.zRot = -rotF;
        rotF += (rotF / f6);

        this.RWingE.zRot = rotF;
        this.LWingE.zRot = -rotF;
        rotF += (rotF / f6);

        this.RWingF.zRot = rotF;
        this.LWingF.zRot = -rotF;
        rotF += (rotF / f6);

        this.RWingG.zRot = rotF;
        this.LWingG.zRot = -rotF;

        if (this.attacking) {
            this.Tail.xRot = 0.5F;
        } else {
            this.Tail.xRot = 0F;
        }

    
	}
}
