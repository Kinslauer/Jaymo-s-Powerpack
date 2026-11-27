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
 * the mole, from {@code MoCModelMole}: its digging hands paddling beside it
 * (of DrZhark's Mo' Creatures, GPL-3, see {@code licenses/mocreatures}), ported to 26.3 by {@code tools/gen_moc_small.py}:
 * its boxes and its animation as they were.
 */

final class MoleModel extends EntityModel<BeastState> {
	private static final float RAD = 57.29578F;
	private final ModelPart Nose, Head, Body, Back, Tail, LLeg, LFingers, RLeg, RFingers, LRearLeg, RRearLeg;

	MoleModel(ModelPart root) {
		super(root);
		this.Nose = root.getChild("Nose");
		this.Head = root.getChild("Head");
		this.Body = root.getChild("Body");
		this.Back = root.getChild("Back");
		this.Tail = root.getChild("Tail");
		this.LLeg = root.getChild("LLeg");
		this.LFingers = root.getChild("LFingers");
		this.RLeg = root.getChild("RLeg");
		this.RFingers = root.getChild("RFingers");
		this.LRearLeg = root.getChild("LRearLeg");
		this.RRearLeg = root.getChild("RRearLeg");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_Nose = root.addOrReplaceChild("Nose", CubeListBuilder.create().texOffs(0, 25).mirror(false).addBox(-1F, 0F, -4F, 2F, 2F, 3F), PartPose.offsetAndRotation(0F, 20F, -6F, 0.2617994F, 0F, 0F));
		PartDefinition p_Head = root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 18).mirror(false).addBox(-3F, -2F, -2F, 6F, 4F, 3F), PartPose.offset(0F, 20F, -6F));
		PartDefinition p_Body = root.addOrReplaceChild("Body", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-5F, 0F, 0F, 10F, 6F, 10F), PartPose.offset(0F, 17F, -6F));
		PartDefinition p_Back = root.addOrReplaceChild("Back", CubeListBuilder.create().texOffs(18, 16).mirror(false).addBox(-4F, -3F, 0F, 8F, 5F, 4F), PartPose.offset(0F, 21F, 4F));
		PartDefinition p_Tail = root.addOrReplaceChild("Tail", CubeListBuilder.create().texOffs(52, 8).mirror(false).addBox(-0.5F, 0F, 1F, 1F, 1F, 5F), PartPose.offsetAndRotation(0F, 21F, 6F, -0.3490659F, 0F, 0F));
		PartDefinition p_LLeg = root.addOrReplaceChild("LLeg", CubeListBuilder.create().texOffs(10, 25).mirror(false).addBox(0F, -2F, -1F, 6F, 4F, 2F), PartPose.offsetAndRotation(4F, 21F, -4F, 0F, 0F, 0.2268928F));
		PartDefinition p_LFingers = root.addOrReplaceChild("LFingers", CubeListBuilder.create().texOffs(44, 8).mirror(false).addBox(5F, -2F, 1F, 1F, 4F, 1F), PartPose.offsetAndRotation(4F, 21F, -4F, 0F, 0F, 0.2268928F));
		PartDefinition p_RLeg = root.addOrReplaceChild("RLeg", CubeListBuilder.create().texOffs(26, 25).mirror(false).addBox(-6F, -2F, -1F, 6F, 4F, 2F), PartPose.offsetAndRotation(-4F, 21F, -4F, 0F, 0F, -0.2268928F));
		PartDefinition p_RFingers = root.addOrReplaceChild("RFingers", CubeListBuilder.create().texOffs(48, 8).mirror(false).addBox(-6F, -2F, 1F, 1F, 4F, 1F), PartPose.offsetAndRotation(-4F, 21F, -4F, 0F, 0F, -0.2268928F));
		PartDefinition p_LRearLeg = root.addOrReplaceChild("LRearLeg", CubeListBuilder.create().texOffs(36, 0).mirror(false).addBox(0F, -2F, -1F, 2F, 3F, 5F), PartPose.offsetAndRotation(3F, 22F, 5F, -0.2792527F, 0.5235988F, 0F));
		PartDefinition p_RRearLeg = root.addOrReplaceChild("RRearLeg", CubeListBuilder.create().texOffs(50, 0).mirror(false).addBox(-2F, -2F, -1F, 2F, 3F, 5F), PartPose.offsetAndRotation(-3F, 22F, 5F, -0.2792527F, -0.5235988F, 0F));
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(BeastState state) {
		super.setupAnim(state);
		rig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
	}

	private void rig(float f, float f1, float f2, float f3, float f4) {

        //super.setRotationAngles(f, f1, f2, f3, f4, 0.0625F);

        this.Head.yRot = f3 / 57.29578F;
        this.Head.xRot = f4 / 57.29578F;
        this.Nose.xRot = 0.2617994F + this.Head.xRot;
        this.Nose.yRot = this.Head.yRot;

        float RLegXRot = Mth.cos((f * 1.0F) + 3.141593F) * 0.8F * f1;
        float LLegXRot = Mth.cos(f * 1.0F) * 0.8F * f1;

        this.RLeg.yRot = RLegXRot;
        this.RFingers.yRot = this.RLeg.yRot;
        this.LLeg.yRot = LLegXRot;
        this.LFingers.yRot = this.LLeg.yRot;
        this.RRearLeg.yRot = -0.5235988F + LLegXRot;
        this.LRearLeg.yRot = 0.5235988F + RLegXRot;

        this.Tail.zRot = this.LLeg.xRot * 0.625F;
    
	}
}
