package com.jaymoskingdoms.client.remnants;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * (0.7.97) An Aether Giant (our own): a hunched, broad-chested giant of the Aether's stone, its small head sunk between
 * heavy shoulders under a crown of shards, long arms ending in great fists that hang to its knees, short thick legs,
 * and shards of its element jutting from its back. A blow brings one fist up and down; a stamp lifts both arms and
 * drops its weight onto them. Texture 128x128 ({@code tools/gen_giants.py} paints its UV layout).
 */
final class GiantModel extends EntityModel<GiantState> {
	/** How far an arm lifts for a blow, how long a blow takes (ticks), and how far the body pitches into a stamp. */
	private static final float BLOW_LIFT = 2.4F, BLOW_TICKS = 12.0F, STAMP_PITCH = 0.35F;
	/** Its hunch (radians), and how far its arms sway as it walks. */
	private static final float HUNCH = 0.22F, ARM_SWAY = 0.6F;

	private final ModelPart body, head, rightArm, leftArm, rightLeg, leftLeg;

	GiantModel(ModelPart root) {
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
		// chest (20x14x12) over hips (16x6x10), and the shards out of its back
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-10, -20, -6, 20, 14, 12)
			.texOffs(0, 26).addBox(-8, -6, -5, 16, 6, 10)
			.texOffs(96, 16).addBox(-6, -27, 2, 4, 10, 4)
			.texOffs(96, 16).addBox(3, -25, 3, 4, 10, 4)
			.texOffs(96, 16).addBox(-1, -23, 5, 3, 7, 3), PartPose.offsetAndRotation(0, 10, 0, HUNCH, 0, 0));
		// the head (8x8x8) with a heavy brow, sunk forward between the shoulders, and its crown of shards
		body.addOrReplaceChild("head", CubeListBuilder.create()
			.texOffs(64, 0).addBox(-4, -7, -6, 8, 8, 8)
			.texOffs(96, 0).addBox(-4.5F, -6, -6.5F, 9, 2, 2)
			.texOffs(112, 16).addBox(-3.5F, -11, -3, 2, 5, 2)
			.texOffs(112, 16).addBox(-1, -12.5F, -2, 2, 6, 2)
			.texOffs(112, 16).addBox(1.5F, -11, -3, 2, 5, 2), PartPose.offsetAndRotation(0, -19, -4, -HUNCH, 0, 0));
		// the arms (6x26x8) with great fists (7x6x9) and shoulder stones (8x5x9)
		body.addOrReplaceChild("right_arm", CubeListBuilder.create()
			.texOffs(0, 42).addBox(-6, -2, -4, 6, 26, 8)
			.texOffs(28, 42).addBox(-6.5F, 22, -4.5F, 7, 6, 9)
			.texOffs(0, 76).addBox(-7, -4, -4.5F, 8, 5, 9), PartPose.offset(-10, -17, 0));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create().mirror()
			.texOffs(0, 42).addBox(0, -2, -4, 6, 26, 8)
			.texOffs(28, 42).addBox(-0.5F, 22, -4.5F, 7, 6, 9)
			.texOffs(0, 76).addBox(-1, -4, -4.5F, 8, 5, 9), PartPose.offset(10, -17, 0));
		// short, thick legs (7x14x7)
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(60, 42).addBox(-3.5F, 0, -3.5F, 7, 14, 7), PartPose.offset(-5, 10, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(60, 42).mirror().addBox(-3.5F, 0, -3.5F, 7, 14, 7),
			PartPose.offset(5, 10, 0));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(GiantState state) {
		super.setupAnim(state);
		float swing = state.walkAnimationPos * 0.4F, amount = Math.min(state.walkAnimationSpeed, 1.0F);
		this.head.yRot = Mth.clamp(state.yRot, -50.0F, 50.0F) * Mth.DEG_TO_RAD;
		this.head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5F;
		this.rightLeg.xRot = Mth.cos(swing) * 0.9F * amount;
		this.leftLeg.xRot = Mth.cos(swing + Mth.PI) * 0.9F * amount;
		// its weight rolls from foot to foot
		this.body.zRot = Mth.cos(swing) * 0.06F * amount;
		this.rightArm.xRot = Mth.cos(swing + Mth.PI) * ARM_SWAY * amount;
		this.leftArm.xRot = Mth.cos(swing) * ARM_SWAY * amount;
		// a slow breath
		float breath = Mth.sin(state.ageInTicks * 0.05F) * 0.03F;
		this.rightArm.zRot = 0.1F + breath;
		this.leftArm.zRot = -0.1F - breath;
		if (state.aggressive && state.leftAttack <= 0.0F && state.rightAttack <= 0.0F) {
			// fists half raised when it's after someone
			this.rightArm.xRot -= 0.35F;
			this.leftArm.xRot -= 0.35F;
		}
		if (state.rightAttack > 0.0F) {
			this.rightArm.xRot = -blow(state.rightAttack);
		}
		if (state.leftAttack > 0.0F) {
			this.leftArm.xRot = -blow(state.leftAttack);
		}
		if (state.stamp) {
			// both arms up, then its whole weight thrown down onto them
			float t = Math.min(Math.max(state.leftAttack, state.rightAttack) / BLOW_TICKS, 1.0F);
			this.body.xRot += STAMP_PITCH * Mth.sin(t * Mth.PI);
		}
	}

	/** How high an arm is raised {@code ticks} into a blow: up over the head, then down hard. */
	private static float blow(float ticks) {
		return BLOW_LIFT * Mth.sin(Math.min(ticks / BLOW_TICKS, 1.0F) * Mth.PI);
	}
}
