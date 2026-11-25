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
 * the mouse, from {@code MoCModelMouse}: head, ears and whiskers turning together, four legs scurrying, the tail swaying
 * (of DrZhark's Mo' Creatures, GPL-3, see {@code licenses/mocreatures}), ported to 26.3 by {@code tools/gen_moc_small.py}:
 * its boxes and its animation as they were.
 */

final class MouseModel extends EntityModel<BeastState> {
	private static final float RAD = 57.29578F;
	private final ModelPart Head, EarR, EarL, WhiskerR, WhiskerL, Tail, FrontL, FrontR, RearL, RearR, BodyF;

	MouseModel(ModelPart root) {
		super(root);
		this.Head = root.getChild("Head");
		this.EarR = root.getChild("EarR");
		this.EarL = root.getChild("EarL");
		this.WhiskerR = root.getChild("WhiskerR");
		this.WhiskerL = root.getChild("WhiskerL");
		this.Tail = root.getChild("Tail");
		this.FrontL = root.getChild("FrontL");
		this.FrontR = root.getChild("FrontR");
		this.RearL = root.getChild("RearL");
		this.RearR = root.getChild("RearR");
		this.BodyF = root.getChild("BodyF");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_Head = root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-1.5F, -1F, -6F, 3F, 4F, 6F), PartPose.offset(0F, 19F, -9F));
		PartDefinition p_EarR = root.addOrReplaceChild("EarR", CubeListBuilder.create().texOffs(16, 26).mirror(false).addBox(-3.5F, -3F, -2F, 3F, 3F, 1F), PartPose.offset(0F, 19F, -9F));
		PartDefinition p_EarL = root.addOrReplaceChild("EarL", CubeListBuilder.create().texOffs(24, 26).mirror(false).addBox(0.5F, -3F, -1F, 3F, 3F, 1F), PartPose.offset(0F, 19F, -10F));
		PartDefinition p_WhiskerR = root.addOrReplaceChild("WhiskerR", CubeListBuilder.create().texOffs(20, 20).mirror(false).addBox(-4.5F, -1F, -7F, 3F, 3F, 1F), PartPose.offset(0F, 19F, -9F));
		PartDefinition p_WhiskerL = root.addOrReplaceChild("WhiskerL", CubeListBuilder.create().texOffs(24, 20).mirror(false).addBox(1.5F, -1F, -6F, 3F, 3F, 1F), PartPose.offset(0F, 19F, -9F));
		PartDefinition p_Tail = root.addOrReplaceChild("Tail", CubeListBuilder.create().texOffs(56, 0).mirror(false).addBox(-0.5F, 0F, -1F, 1F, 14F, 1F), PartPose.offsetAndRotation(0F, 20F, 3F, 1.570796F, 0F, 0F));
		PartDefinition p_FrontL = root.addOrReplaceChild("FrontL", CubeListBuilder.create().texOffs(0, 18).mirror(false).addBox(-2F, 0F, -3F, 2F, 1F, 4F), PartPose.offset(3F, 23F, -7F));
		PartDefinition p_FrontR = root.addOrReplaceChild("FrontR", CubeListBuilder.create().texOffs(0, 18).mirror(false).addBox(0F, 0F, -3F, 2F, 1F, 4F), PartPose.offset(-3F, 23F, -7F));
		PartDefinition p_RearL = root.addOrReplaceChild("RearL", CubeListBuilder.create().texOffs(0, 18).mirror(false).addBox(-2F, 0F, -4F, 2F, 1F, 4F), PartPose.offset(4F, 23F, 2F));
		PartDefinition p_RearR = root.addOrReplaceChild("RearR", CubeListBuilder.create().texOffs(0, 18).mirror(false).addBox(0F, 0F, -4F, 2F, 1F, 4F), PartPose.offset(-4F, 23F, 2F));
		PartDefinition p_BodyF = root.addOrReplaceChild("BodyF", CubeListBuilder.create().texOffs(20, 0).mirror(false).addBox(-3F, -3F, -7F, 6F, 6F, 12F), PartPose.offset(0F, 20F, -2F));
		return LayerDefinition.create(mesh, 64, 32);
	}

	@Override
	public void setupAnim(BeastState state) {
		super.setupAnim(state);
		rig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
	}

	private void rig(float f, float f1, float f2, float f3, float f4) {

        this.Head.xRot = -(f4 / 57.29578F);
        this.Head.yRot = f3 / 57.29578F;
        this.EarR.xRot = this.Head.xRot;
        this.EarR.yRot = this.Head.yRot;
        this.EarL.xRot = this.Head.xRot;
        this.EarL.yRot = this.Head.yRot;
        this.WhiskerR.xRot = this.Head.xRot;
        this.WhiskerR.yRot = this.Head.yRot;
        this.WhiskerL.xRot = this.Head.xRot;
        this.WhiskerL.yRot = this.Head.yRot;
        this.FrontL.xRot = Mth.cos(f * 0.6662F) * 0.6F * f1;
        this.RearL.xRot = Mth.cos((f * 0.6662F) + 3.141593F) * 0.8F * f1;
        this.RearR.xRot = Mth.cos(f * 0.6662F) * 0.6F * f1;
        this.FrontR.xRot = Mth.cos((f * 0.6662F) + 3.141593F) * 0.8F * f1;
        this.Tail.yRot = this.FrontL.xRot * 0.625F;
    
	}
}
