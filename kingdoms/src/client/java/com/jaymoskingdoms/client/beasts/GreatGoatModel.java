package com.jaymoskingdoms.client.beasts;

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
 * The great goat ({@code beasts/GreatGoat}), our own model: a deep, long body on four long sure legs, a thick neck
 * carrying the head high, a bearded muzzle, and a pair of heavy horns sweeping back from the brow. Saddled, it wears a
 * Stoneborn riding saddle. A kid has no horns yet and a bigger head for its size. Its poses: the walk, the head where it
 * looks, and the head down to crop the grass. {@code tools/gen_great_goat.py} mirrors these boxes (keep both the same)
 * and paints the coat.
 */
final class GreatGoatModel extends EntityModel<GreatGoatRenderer.State> {
	/** The walk's swing (radians at full stride), how far the head turns to look, and a kid's head for its size. */
	static final float STRIDE = 1.0F, LOOK = 0.8F, KID_HEAD = 1.3F;
	/** Cropping grass: the head's turn down (radians). */
	static final float GRAZE_TURN = 1.2F;

	private final ModelPart body, saddle, head, horns, tail, rightFrontLeg, leftFrontLeg, rightHindLeg, leftHindLeg;

	GreatGoatModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.saddle = this.body.getChild("saddle");
		this.head = root.getChild("head");
		this.horns = this.head.getChild("horns");
		this.tail = this.body.getChild("tail");
		this.rightFrontLeg = root.getChild("right_front_leg");
		this.leftFrontLeg = root.getChild("left_front_leg");
		this.rightHindLeg = root.getChild("right_hind_leg");
		this.leftHindLeg = root.getChild("left_hind_leg");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-5.0F, 6.0F, -9.0F, 10.0F, 10.0F, 18.0F, CubeDeformation.NONE), PartPose.ZERO);
		body.addOrReplaceChild("saddle", CubeListBuilder.create()
			.texOffs(24, 42).addBox(-5.5F, 5.0F, -4.0F, 11.0F, 2.0F, 8.0F, new CubeDeformation(0.1F)), PartPose.ZERO);
		body.addOrReplaceChild("tail", CubeListBuilder.create()
			.texOffs(56, 9).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 3.0F, 1.0F, CubeDeformation.NONE), PartPose.offset(0.0F, 7.0F, 9.0F));
		// the neck, the skull, the muzzle and the beard
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(0, 28).addBox(-2.5F, -6.0F, -3.0F, 5.0F, 9.0F, 5.0F, CubeDeformation.NONE)
				.texOffs(20, 28).addBox(-3.0F, -12.0F, -6.0F, 6.0F, 6.0F, 7.0F, CubeDeformation.NONE)
				.texOffs(46, 28).addBox(-2.0F, -10.0F, -9.0F, 4.0F, 4.0F, 3.0F, CubeDeformation.NONE)
				.texOffs(46, 35).addBox(-1.0F, -6.0F, -8.0F, 2.0F, 4.0F, 2.0F, CubeDeformation.NONE),
			PartPose.offset(0.0F, 8.0F, -8.0F));
		// the horns: up from the brow, swept back, and down at the tips
		CubeListBuilder horns = CubeListBuilder.create();
		for (int side = -1; side <= 1; side += 2) {
			float x = side > 0 ? 0.5F : -2.5F;
			horns.texOffs(56, 0).addBox(x, -15.0F, -4.0F, 2.0F, 3.0F, 2.0F, CubeDeformation.NONE)
				.texOffs(0, 54).addBox(x, -16.0F, -2.0F, 2.0F, 2.0F, 4.0F, CubeDeformation.NONE)
				.texOffs(56, 5).addBox(x, -15.0F, 2.0F, 2.0F, 2.0F, 2.0F, CubeDeformation.NONE);
		}
		head.addOrReplaceChild("horns", horns, PartPose.ZERO);
		for (int side = -1; side <= 1; side += 2) {
			root.addOrReplaceChild(side > 0 ? "right_front_leg" : "left_front_leg", CubeListBuilder.create()
				.texOffs(0, 42).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 9.0F, 3.0F, CubeDeformation.NONE), PartPose.offset(3.0F * side, 15.0F, -6.0F));
			root.addOrReplaceChild(side > 0 ? "right_hind_leg" : "left_hind_leg", CubeListBuilder.create()
				.texOffs(12, 42).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 9.0F, 3.0F, CubeDeformation.NONE), PartPose.offset(3.0F * side, 15.0F, 6.0F));
		}
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(GreatGoatRenderer.State state) {
		super.setupAnim(state);
		this.horns.visible = !state.isBaby;
		this.saddle.visible = state.saddled;
		float headSize = state.isBaby ? KID_HEAD : 1.0F;
		this.head.xScale = headSize;
		this.head.yScale = headSize;
		this.head.zScale = headSize;
		float swing = state.walkAnimationPos * 0.6662F, amount = STRIDE * Math.min(1.0F, state.walkAnimationSpeed);
		this.leftFrontLeg.xRot = Mth.cos(swing) * amount;
		this.rightFrontLeg.xRot = Mth.cos(swing + Mth.PI) * amount;
		this.leftHindLeg.xRot = Mth.cos(swing + Mth.PI) * amount;
		this.rightHindLeg.xRot = Mth.cos(swing) * amount;
		this.tail.xRot = 0.3F + Mth.sin(state.ageInTicks * 0.3F) * 0.1F;
		this.head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD, -LOOK, LOOK);
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.6F;
		if (state.graze > 0.0F) {
			this.head.xRot = Mth.lerp(state.graze, this.head.xRot, GRAZE_TURN + Mth.sin(state.ageInTicks * 0.8F) * 0.06F);
			this.head.yRot *= 1.0F - state.graze;
		}
	}
}
