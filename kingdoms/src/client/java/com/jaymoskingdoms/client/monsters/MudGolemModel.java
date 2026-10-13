package com.jaymoskingdoms.client.monsters;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * (0.5.56) The mud golem (our own): a hunched heap of mud with a sagging belly, a lumpy brow over glowing eyes, long
 * arms that hang to its knees and swing as it trudges, both brought down at once on a blow. Texture 64x64.
 */
final class MudGolemModel extends EntityModel<LegendState> {
	/** How far its arms lift for a blow, and how long the blow takes (ticks). */
	private static final float BLOW_LIFT = 2.2F, BLOW_TICKS = 10.0F;

	private final ModelPart body, head, rightArm, leftArm, rightLeg, leftLeg;

	MudGolemModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = this.body.getChild("head");
		this.rightArm = this.body.getChild("right_arm");
		this.leftArm = this.body.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7, -12, -4.5F, 14, 12, 9)
			.texOffs(0, 21).addBox(-5, 0, -4, 10, 4, 8), PartPose.offsetAndRotation(0, 14, 0, 0.12F, 0, 0));
		body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 33).addBox(-4, -7, -5, 8, 7, 8)
			.texOffs(32, 0).addBox(-4.5F, -6, -5.5F, 9, 2, 2), PartPose.offset(0, -11, -2));
		body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(36, 21).addBox(-4, -2, -3, 5, 18, 6), PartPose.offset(-8, -9, 0));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(36, 21).mirror().addBox(-1, -2, -3, 5, 18, 6), PartPose.offset(8, -9, 0));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 48).addBox(-3, 0, -3, 6, 10, 6), PartPose.offset(-3.5F, 14, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 48).mirror().addBox(-3, 0, -3, 6, 10, 6), PartPose.offset(3.5F, 14, 0));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(LegendState state) {
		super.setupAnim(state);
		float swing = state.walkAnimationPos * 0.5F, amount = state.walkAnimationSpeed;
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
		this.rightLeg.xRot = Mth.cos(swing) * 1.2F * amount;
		this.leftLeg.xRot = Mth.cos(swing + Mth.PI) * 1.2F * amount;
		this.body.zRot = Mth.cos(swing) * 0.08F * amount;
		float blow = Math.max(state.leftAttack, state.rightAttack);
		if (blow > 0.0F) {
			// up over the head, then down hard
			float lift = BLOW_LIFT * Mth.sin(Math.min(blow / BLOW_TICKS, 1.0F) * Mth.PI);
			this.rightArm.xRot = this.leftArm.xRot = -lift;
		} else {
			this.rightArm.xRot = Mth.cos(swing + Mth.PI) * 0.9F * amount;
			this.leftArm.xRot = Mth.cos(swing) * 0.9F * amount;
		}
		this.rightArm.zRot = 0.08F + Mth.cos(state.ageInTicks * 0.06F) * 0.04F;
		this.leftArm.zRot = -this.rightArm.zRot;
	}
}
