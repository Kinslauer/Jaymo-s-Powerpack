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
 * the fly, from {@code MoCModelFly}: wings beating in the air and folded on the ground, legs walking
 * (of DrZhark's Mo' Creatures, GPL-3, see {@code licenses/mocreatures}), ported to 26.3 by {@code tools/gen_moc_small.py}:
 * its boxes and its animation as they were.
 */

final class FlyModel extends EntityModel<BeastState> {
	private static final float RAD = 57.29578F;
	private final ModelPart Head, Thorax, Abdomen, Tail, FrontLegs, RearLegs, MidLegs, LeftWing, RightWing, FoldedWings;
	private boolean isFlying;

	FlyModel(ModelPart root) {
		super(root);
		this.Head = root.getChild("Head");
		this.Thorax = root.getChild("Thorax");
		this.Abdomen = root.getChild("Abdomen");
		this.Tail = root.getChild("Tail");
		this.FrontLegs = root.getChild("FrontLegs");
		this.RearLegs = root.getChild("RearLegs");
		this.MidLegs = root.getChild("MidLegs");
		this.LeftWing = root.getChild("LeftWing");
		this.RightWing = root.getChild("RightWing");
		this.FoldedWings = root.getChild("FoldedWings");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_Head = root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 4).mirror(false).addBox(-1F, 0F, -1F, 2F, 1F, 2F), PartPose.offsetAndRotation(0F, 21.5F, -2F, -2.171231F, 0F, 0F));
		PartDefinition p_Thorax = root.addOrReplaceChild("Thorax", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offset(0F, 20.5F, -1F));
		PartDefinition p_Abdomen = root.addOrReplaceChild("Abdomen", CubeListBuilder.create().texOffs(8, 0).mirror(false).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 21.5F, 0F, 1.427659F, 0F, 0F));
		PartDefinition p_Tail = root.addOrReplaceChild("Tail", CubeListBuilder.create().texOffs(10, 2).mirror(false).addBox(-1F, 0F, -1F, 1F, 1F, 1F), PartPose.offsetAndRotation(0.5F, 21.2F, 1.5F, 1.427659F, 0F, 0F));
		PartDefinition p_FrontLegs = root.addOrReplaceChild("FrontLegs", CubeListBuilder.create().texOffs(0, 7).mirror(false).addBox(-1F, 0F, 0F, 2F, 2F, 0F), PartPose.offsetAndRotation(0F, 22.5F, -1.8F, 0.1487144F, 0F, 0F));
		PartDefinition p_RearLegs = root.addOrReplaceChild("RearLegs", CubeListBuilder.create().texOffs(0, 11).mirror(false).addBox(-1F, 0F, 0F, 2F, 2F, 0F), PartPose.offsetAndRotation(0F, 22.5F, -0.4F, 1.070744F, 0F, 0F));
		PartDefinition p_MidLegs = root.addOrReplaceChild("MidLegs", CubeListBuilder.create().texOffs(0, 9).mirror(false).addBox(-1F, 0F, 0F, 2F, 2F, 0F), PartPose.offsetAndRotation(0F, 22.5F, -1.2F, 0.5948578F, 0F, 0F));
		PartDefinition p_LeftWing = root.addOrReplaceChild("LeftWing", CubeListBuilder.create().texOffs(4, 4).mirror(false).addBox(-1F, 0F, 0.5F, 2F, 0F, 4F), PartPose.offsetAndRotation(0F, 20.4F, -1F, 0F, 1.047198F, 0F));
		PartDefinition p_RightWing = root.addOrReplaceChild("RightWing", CubeListBuilder.create().texOffs(4, 4).mirror(false).addBox(-1F, 0F, 0.5F, 2F, 0F, 4F), PartPose.offsetAndRotation(0F, 20.4F, -1F, 0F, -1.047198F, 0F));
		PartDefinition p_FoldedWings = root.addOrReplaceChild("FoldedWings", CubeListBuilder.create().texOffs(4, 4).mirror(false).addBox(-1F, 0F, 0F, 2F, 0F, 4F), PartPose.offsetAndRotation(0F, 20.5F, -2F, 0.0872665F, 0F, 0F));
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(BeastState state) {
		super.setupAnim(state);
		this.isFlying = state.airborne;
		for (ModelPart p : List.of(LeftWing, RightWing)) {
			p.visible = this.isFlying;
		}
		for (ModelPart p : List.of(FoldedWings)) {
			p.visible = !this.isFlying;
		}
		rig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
	}

	private void rig(float f, float f1, float f2, float f3, float f4) {
		float wingRot = Mth.cos(f2 * 3.0F) * 0.7F;
		this.RightWing.zRot = wingRot;
		this.LeftWing.zRot = -wingRot;
		float legMov;
		float legMovB;
		if (this.isFlying) {
			legMov = f1 * 1.5F;
			legMovB = legMov;
		} else {
			legMov = Mth.cos(f * 1.5F + 3.141593F) * 2.0F * f1;
			legMovB = Mth.cos(f * 1.5F) * 2.0F * f1;
		}
		this.FrontLegs.xRot = 0.1487144F + legMov;
		this.MidLegs.xRot = 0.5948578F + legMovB;
		this.RearLegs.xRot = 1.070744F + legMov;
	}
}
