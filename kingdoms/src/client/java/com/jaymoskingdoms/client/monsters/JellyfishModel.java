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
 * the jellyfish, from {@code MoCModelJellyFish}: its tentacles trailing as it moves
 * (of DrZhark's Mo' Creatures, GPL-3, see {@code licenses/mocreatures}), ported to 26.3 by {@code tools/gen_moc_small.py}:
 * its boxes and its animation as they were.
 */

final class JellyfishModel extends EntityModel<BeastState> {
	private static final float RAD = 57.29578F;
	private final ModelPart Top, Head, HeadSmall, Body, BodyCenter, BodyBottom, Side1, Side2, Side3, Side4, LegSmall1, LegC1, LegC2, LegC3, Leg1, Leg2, Leg3, Leg4, Leg5, Leg6, Leg7, Leg8, Leg9;

	JellyfishModel(ModelPart root) {
		super(root);
		this.Top = root.getChild("Top");
		this.Head = root.getChild("Head");
		this.HeadSmall = root.getChild("HeadSmall");
		this.Body = root.getChild("Body");
		this.BodyCenter = root.getChild("BodyCenter");
		this.BodyBottom = root.getChild("BodyBottom");
		this.Side1 = root.getChild("Side1");
		this.Side2 = root.getChild("Side2");
		this.Side3 = root.getChild("Side3");
		this.Side4 = root.getChild("Side4");
		this.LegSmall1 = root.getChild("LegSmall1");
		this.LegC1 = root.getChild("LegC1");
		this.LegC2 = root.getChild("LegC2");
		this.LegC3 = root.getChild("LegC3");
		this.Leg1 = root.getChild("Leg1");
		this.Leg2 = root.getChild("Leg2");
		this.Leg3 = root.getChild("Leg3");
		this.Leg4 = root.getChild("Leg4");
		this.Leg5 = root.getChild("Leg5");
		this.Leg6 = root.getChild("Leg6");
		this.Leg7 = root.getChild("Leg7");
		this.Leg8 = root.getChild("Leg8");
		this.Leg9 = root.getChild("Leg9");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_Top = root.addOrReplaceChild("Top", CubeListBuilder.create().texOffs(0, 10).mirror(false).addBox(-2.5F, 0F, -2.5F, 5F, 1F, 5F), PartPose.offset(0F, 11F, 0F));
		PartDefinition p_Head = root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-4F, 0F, -4F, 8F, 2F, 8F), PartPose.offset(0F, 12F, 0F));
		PartDefinition p_HeadSmall = root.addOrReplaceChild("HeadSmall", CubeListBuilder.create().texOffs(24, 0).mirror(false).addBox(-2F, 0F, -2F, 4F, 3F, 4F), PartPose.offset(0F, 12.5F, 0F));
		PartDefinition p_Body = root.addOrReplaceChild("Body", CubeListBuilder.create().texOffs(36, 0).mirror(false).addBox(-3.5F, 0F, -3.5F, 7F, 7F, 7F), PartPose.offset(0F, 13.8F, 0F));
		PartDefinition p_BodyCenter = root.addOrReplaceChild("BodyCenter", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-1F, 0F, -1F, 2F, 3F, 2F), PartPose.offset(0F, 15.5F, 0F));
		PartDefinition p_BodyBottom = root.addOrReplaceChild("BodyBottom", CubeListBuilder.create().texOffs(20, 10).mirror(false).addBox(-2F, 0F, -2F, 4F, 2F, 4F), PartPose.offset(0F, 18.3F, 0F));
		PartDefinition p_Side1 = root.addOrReplaceChild("Side1", CubeListBuilder.create().texOffs(20, 10).mirror(false).addBox(-2F, 5F, 0F, 4F, 2F, 4F), PartPose.offsetAndRotation(0F, 12.5F, 0F, -0.7679449F, 0F, 0F));
		PartDefinition p_Side2 = root.addOrReplaceChild("Side2", CubeListBuilder.create().texOffs(20, 10).mirror(false).addBox(-4F, 5F, -2F, 4F, 2F, 4F), PartPose.offsetAndRotation(0F, 12.5F, 0F, 0F, 0F, -0.7679449F));
		PartDefinition p_Side3 = root.addOrReplaceChild("Side3", CubeListBuilder.create().texOffs(20, 10).mirror(false).addBox(0F, 5F, -2F, 4F, 2F, 4F), PartPose.offsetAndRotation(0F, 12.5F, 0F, 0F, 0F, 0.7679449F));
		PartDefinition p_Side4 = root.addOrReplaceChild("Side4", CubeListBuilder.create().texOffs(20, 10).mirror(false).addBox(-2F, 5F, -4F, 4F, 2F, 4F), PartPose.offsetAndRotation(0F, 12.5F, 0F, 0.7679449F, 0F, 0F));
		PartDefinition p_LegSmall1 = root.addOrReplaceChild("LegSmall1", CubeListBuilder.create().texOffs(60, 2).mirror(false).addBox(-1F, 0F, -1F, 1F, 3F, 1F), PartPose.offset(0F, 18.5F, 0F));
		PartDefinition p_LegC1 = root.addOrReplaceChild("LegC1", CubeListBuilder.create().texOffs(15, 10).mirror(false).addBox(-1F, 0F, -1F, 1F, 4F, 1F), PartPose.offsetAndRotation(-0.5F, 15.5F, -0.5F, -0.2602503F, 0F, 0.1487144F));
		PartDefinition p_LegC2 = root.addOrReplaceChild("LegC2", CubeListBuilder.create().texOffs(15, 10).mirror(false).addBox(-1F, 0F, 0F, 1F, 4F, 1F), PartPose.offsetAndRotation(0.5F, 15.5F, -0.5F, 0.1487144F, 1.747395F, 0F));
		PartDefinition p_LegC3 = root.addOrReplaceChild("LegC3", CubeListBuilder.create().texOffs(15, 10).mirror(false).addBox(-1F, 0F, 0F, 1F, 4F, 1F), PartPose.offsetAndRotation(-0.5F, 15.5F, 0.5F, 0.1115358F, 0.3717861F, 0.2230717F));
		PartDefinition p_Leg1 = root.addOrReplaceChild("Leg1", CubeListBuilder.create().texOffs(0, 10).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offset(0F, 20F, 2.5F));
		PartDefinition p_Leg2 = root.addOrReplaceChild("Leg2", CubeListBuilder.create().texOffs(0, 10).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offset(0F, 20F, -2.5F));
		PartDefinition p_Leg3 = root.addOrReplaceChild("Leg3", CubeListBuilder.create().texOffs(0, 10).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offset(2.5F, 20F, 0F));
		PartDefinition p_Leg4 = root.addOrReplaceChild("Leg4", CubeListBuilder.create().texOffs(0, 10).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offset(-2.5F, 20F, 0F));
		PartDefinition p_Leg5 = root.addOrReplaceChild("Leg5", CubeListBuilder.create().texOffs(0, 10).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(2F, 20F, 2F, 0F, 0.7853982F, 0F));
		PartDefinition p_Leg6 = root.addOrReplaceChild("Leg6", CubeListBuilder.create().texOffs(0, 10).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(2F, 20F, -2F, 0F, 0.7853982F, 0F));
		PartDefinition p_Leg7 = root.addOrReplaceChild("Leg7", CubeListBuilder.create().texOffs(0, 10).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(-2F, 20F, -2F, 0F, 0.7853982F, 0F));
		PartDefinition p_Leg8 = root.addOrReplaceChild("Leg8", CubeListBuilder.create().texOffs(60, 0).mirror(false).addBox(0F, 0F, 0F, 1F, 5F, 1F), PartPose.offset(0F, 18.5F, 0F));
		PartDefinition p_Leg9 = root.addOrReplaceChild("Leg9", CubeListBuilder.create().texOffs(0, 10).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(-2F, 20F, 2F, 0F, 0.7853982F, 0F));
		return LayerDefinition.create(mesh, 64, 16);
	}

	@Override
	public void setupAnim(BeastState state) {
		super.setupAnim(state);
		rig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
	}

	private void rig(float f, float f1, float f2, float f3, float f4) {

        float f6 = f1 * 2.0F;
        if (f6 > 1.0F) {
            f6 = 1.0F;
        }

        this.LegSmall1.xRot = f6;
        this.LegC1.xRot = f6;
        this.LegC2.xRot = f6;
        this.LegC3.xRot = f6;
        this.Leg1.xRot = f6;
        this.Leg2.xRot = f6;
        this.Leg3.xRot = f6;
        this.Leg4.xRot = f6;
        this.Leg5.xRot = f6;
        this.Leg6.xRot = f6;
        this.Leg7.xRot = f6;
        this.Leg8.xRot = f6;
        this.Leg9.xRot = f6;
    
	}
}
