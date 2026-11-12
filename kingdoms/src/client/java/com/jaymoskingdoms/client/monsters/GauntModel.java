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
 * The Gaunt ({@link com.jaymoskingdoms.hunger.Gaunt}): our own boxes, a tall starved figure (about two and seven eighths
 * blocks) with a ribbed torso, a long skull with a narrow muzzle and a hanging jaw, branching bone antlers, arms that reach
 * to the knees and thin legs. Stalking, it leans forward with its arms hanging to the ground; striking, both arms rake down
 * and the jaw drops. Its skin is painted by {@code tools/gen_hunger_picks.py}.
 */
final class GauntModel extends EntityModel<LegendState> {
	/** The hip's height in the model's own units, the torso's length and the shoulders' drop from its top. */
	private static final float HIP = 8.0F, TORSO = 16.0F, SHOULDER = 14.0F;
	/** How far it leans forward when stalking (radians). */
	private static final float LEAN = 0.55F;

	private final ModelPart head, jaw, body, rightArm, leftArm, rightLeg, leftLeg;

	GauntModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.jaw = this.head.getChild("jaw");
		this.body = root.getChild("body");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3, -8, -3, 6, 8, 6),
			PartPose.offset(0, HIP - TORSO, 0));
		head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5F, -3, -6, 3, 3, 3), PartPose.ZERO);
		head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(14, 16).addBox(-1.5F, 0, -5.5F, 3, 2, 3), PartPose.offset(0, -2, 0));
		for (int side = -1; side <= 1; side += 2) {
			boolean mirror = side > 0;
			CubeListBuilder beam = CubeListBuilder.create().texOffs(0, 24).mirror(mirror).addBox(-0.5F, -7, -0.5F, 1, 7, 1);
			CubeListBuilder tine = CubeListBuilder.create().texOffs(6, 24).mirror(mirror).addBox(-0.5F, -4, -0.5F, 1, 4, 1);
			PartDefinition antler = head.addOrReplaceChild(mirror ? "antler_left" : "antler_right", beam,
				PartPose.offsetAndRotation(side * 3.5F, -7.5F, 0, 0, 0, side * 0.25F));
			antler.addOrReplaceChild("low_tine", tine, PartPose.offsetAndRotation(0, -2.0F, 0, 0, 0, side * 0.9F));
			antler.addOrReplaceChild("high_tine", tine, PartPose.offsetAndRotation(0, -5.0F, 0, -0.5F, 0, side * 0.7F));
		}
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(32, 0).addBox(-4, -TORSO, -2, 8, TORSO, 4), PartPose.offset(0, HIP, 0));
		root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(60, 0).addBox(-1.5F, -1, -1.5F, 3, 20, 3),
			PartPose.offset(-5.5F, HIP - SHOULDER, 0));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(60, 0).mirror().addBox(-1.5F, -1, -1.5F, 3, 20, 3),
			PartPose.offset(5.5F, HIP - SHOULDER, 0));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(76, 0).addBox(-1.5F, 0, -1.5F, 3, 16, 3), PartPose.offset(-2, HIP, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(76, 0).mirror().addBox(-1.5F, 0, -1.5F, 3, 16, 3), PartPose.offset(2, HIP, 0));
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(LegendState state) {
		super.setupAnim(state);
		float lean = state.hunched ? LEAN : 0.0F;
		this.body.xRot = lean;
		// the head and shoulders ride the torso's top as it leans
		float cos = Mth.cos(lean), sin = Mth.sin(lean);
		this.head.setPos(0, HIP - TORSO * cos, -TORSO * sin);
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD - lean * 0.6F;
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
		this.rightArm.setPos(-5.5F, HIP - SHOULDER * cos, -SHOULDER * sin);
		this.leftArm.setPos(5.5F, HIP - SHOULDER * cos, -SHOULDER * sin);
		float swing = state.walkAnimationPos * 0.6662F, amount = Math.min(1.0F, state.walkAnimationSpeed * 1.4F);
		this.rightLeg.xRot = Mth.cos(swing) * 1.1F * amount;
		this.leftLeg.xRot = Mth.cos(swing + Mth.PI) * 1.1F * amount;
		float sway = Mth.sin(state.ageInTicks * 0.05F) * 0.04F;
		this.rightArm.xRot = -lean * 0.9F + Mth.cos(swing + Mth.PI) * 0.7F * amount + sway;
		this.leftArm.xRot = -lean * 0.9F + Mth.cos(swing) * 0.7F * amount - sway;
		this.rightArm.zRot = 0.06F;
		this.leftArm.zRot = -0.06F;
		// a blow: both arms up and raking down; the jaw drops with it
		float blow = Math.max(state.leftAttack, state.rightAttack);
		if (blow > 0.0F) {
			float rake = Mth.sin(Math.min(1.0F, blow / 10.0F) * Mth.PI);
			this.rightArm.xRot = -2.4F * rake;
			this.leftArm.xRot = -2.4F * rake;
		}
		this.jaw.xRot = state.aggressive ? 0.5F + Mth.sin(state.ageInTicks * 0.3F) * 0.08F : 0.12F;
	}
}
