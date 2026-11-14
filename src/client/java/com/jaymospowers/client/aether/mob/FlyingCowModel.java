/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/FlyingCowModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, posed from our own render state.
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.mob;


import com.jaymospowers.client.aether.anim.FlyingCowAnimation;
import com.jaymospowers.client.aether.anim.WingAnimation;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

final class FlyingCowModel extends AetherAnimalModel {
	/** How far the wings beat in the air (radians). */
	private static final float WING_BEAT = 0.35F;
	private final ModelPart body;
	private final ModelPart saddle;
	private final ModelPart wing_left;
	private final ModelPart wing_right;
	private final ModelPart tail;
	private final ModelPart head;
	private final ModelPart ear_left;
	private final ModelPart ear_right;
	private final ModelPart leg_front_right;
	private final ModelPart leg_front_left;
	private final ModelPart leg_back_left;
	private final ModelPart leg_back_right;

	public final KeyframeAnimation openWingAnimation;
	public final KeyframeAnimation babyAnimation;

	private FlyingCowModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.saddle = this.body.getChild("saddle");
		this.head = this.body.getChild("head");
		this.ear_left = this.head.getChild("ear_left");
		this.ear_right = this.head.getChild("ear_right");
		this.wing_left = this.body.getChild("wing_left");
		this.wing_right = this.body.getChild("wing_right");
		this.leg_front_left = this.body.getChild("leg_front_left");
		this.leg_front_right = this.body.getChild("leg_front_right");
		this.leg_back_left = this.body.getChild("leg_back_left");
		this.leg_back_right = this.body.getChild("leg_back_right");
		this.tail = this.body.getChild("tail");
		this.babyAnimation = FlyingCowAnimation.BABY.bake(root);
		this.openWingAnimation = WingAnimation.OPEN_WINGS.bake(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 21).addBox(-5.0F, -1.0F, 0.0F, 10.0F, 10.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 7.0F, -6.0F));

		PartDefinition wing_left = body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(26, 47).addBox(-1.0F, -5.0F, -5.5F, 2.0F, 18.0F, 11.0F, new CubeDeformation(0.0F))
				.texOffs(32, 72).mirror().addBox(0.0F, 12.0F, -2.0F, 0.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(5.0F, -1.0F, 7.5F, 0.0F, 0.0F, -0.3054F));

		PartDefinition feather_left_5_r1 = wing_left.addOrReplaceChild("feather_left_5_r1", CubeListBuilder.create().texOffs(40, 76).mirror().addBox(0.0F, -4.0F, -4.0F, 0.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 7.0F, 7.75F, 0.6109F, 0.0F, 0.0F));

		PartDefinition feather_left_4_r1 = wing_left.addOrReplaceChild("feather_left_4_r1", CubeListBuilder.create().texOffs(24, 76).mirror().addBox(0.0F, -4.0F, 0.0F, 0.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 7.0F, -7.75F, -0.6109F, 0.0F, 0.0F));

		PartDefinition feather_left_3_r1 = wing_left.addOrReplaceChild("feather_left_3_r1", CubeListBuilder.create().texOffs(24, 72).mirror().addBox(0.0F, -1.0F, -2.0F, 0.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 12.0F, -3.75F, -0.3491F, 0.0F, 0.0F));

		PartDefinition feather_left_2_r1 = wing_left.addOrReplaceChild("feather_left_2_r1", CubeListBuilder.create().texOffs(40, 72).mirror().addBox(0.0F, -1.0F, 0.75F, 0.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 13.0F, 1.25F, 0.3491F, 0.0F, 0.0F));

		PartDefinition wing_right = body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(0, 47).addBox(-1.0F, -5.0F, -5.5F, 2.0F, 18.0F, 11.0F, new CubeDeformation(0.0F))
				.texOffs(8, 72).addBox(0.0F, 12.0F, -2.0F, 0.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0F, -1.0F, 7.5F, 0.0F, 0.0F, 0.3054F));

		PartDefinition feather_right_5_r1 = wing_right.addOrReplaceChild("feather_right_5_r1", CubeListBuilder.create().texOffs(0, 76).addBox(0.0F, -4.0F, -4.0F, 0.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 7.0F, 7.75F, 0.6109F, 0.0F, 0.0F));

		PartDefinition feather_right_4_r1 = wing_right.addOrReplaceChild("feather_right_4_r1", CubeListBuilder.create().texOffs(16, 76).addBox(0.0F, -4.0F, 0.0F, 0.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 7.0F, -7.75F, -0.6109F, 0.0F, 0.0F));

		PartDefinition feather_right_3_r1 = wing_right.addOrReplaceChild("feather_right_3_r1", CubeListBuilder.create().texOffs(16, 72).addBox(0.0F, -1.0F, -2.0F, 0.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 12.0F, -3.75F, -0.3491F, 0.0F, 0.0F));

		PartDefinition feather_right_2_r1 = wing_right.addOrReplaceChild("feather_right_2_r1", CubeListBuilder.create().texOffs(0, 72).addBox(0.0F, -1.0F, 0.75F, 0.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 13.0F, 1.25F, 0.3491F, 0.0F, 0.0F));

		PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 16.0F));

		PartDefinition tail_r1 = tail.addOrReplaceChild("tail_r1", CubeListBuilder.create().texOffs(36, 26).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 11.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.2182F, 0.0F, 0.0F));

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 8).addBox(-4.0F, -5.0F, -5.0F, 8.0F, 8.0F, 5.0F, new CubeDeformation(0.0F))
				.texOffs(26, 16).addBox(-3.0F, 0.0F, -7.0F, 6.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
				.texOffs(0, 0).addBox(-4.0F, -11.0F, -5.0F, 8.0F, 6.0F, 2.0F, new CubeDeformation(0.0F))
				.texOffs(34, 0).addBox(2.0F, -9.5F, -1.0F, 7.0F, 6.0F, 0.0F, new CubeDeformation(0.0F))
				.texOffs(20, 0).addBox(-9.0F, -9.5F, -1.0F, 7.0F, 6.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 2.0F, 0.0F));

		PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create(), PartPose.offset(4.0F, -1.0F, -3.0F));

		PartDefinition ear_left_r1 = ear_left.addOrReplaceChild("ear_left_r1", CubeListBuilder.create().texOffs(29, 6).addBox(0.0F, -3.0F, 0.0F, 4.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1325F, -0.6429F, -0.2187F));

		PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create(), PartPose.offset(-4.0F, -1.0F, -3.0F));

		PartDefinition ear_right_r1 = ear_right.addOrReplaceChild("ear_right_r1", CubeListBuilder.create().texOffs(21, 6).addBox(-4.0F, -3.0F, 0.0F, 4.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1325F, 0.6429F, 0.2187F));

		PartDefinition leg_front_right = body.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(0, 87).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 9.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-3.0F, 9.0F, 2.0F));

		PartDefinition leg_front_left = body.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(12, 87).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 9.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(3.0F, 9.0F, 2.0F));

		PartDefinition leg_back_left = body.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(12, 99).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 9.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(3.0F, 9.0F, 15.0F));

		PartDefinition leg_back_right = body.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(0, 99).addBox(-1.5F, -1.0F, -1.5F, 3.0F, 9.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-3.0F, 9.0F, 15.0F));

		// our own saddle: a pad over the middle of the back, on rows we added under the sheet
		body.addOrReplaceChild("saddle", CubeListBuilder.create().texOffs(0, 128).addBox(-5.0F, -1.0F, 4.0F, 10.0F, 5.0F, 8.0F, new CubeDeformation(0.5F)), PartPose.ZERO);

		return LayerDefinition.create(meshdefinition, 64, 141);
	}

	static FlyingCowModel create() {
		return new FlyingCowModel(createBodyLayer().bakeRoot());
	}


	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		this.leg_back_right.xRot = Mth.cos(state.walkAnimationPos * 0.6662F) * 1.4F * speed;
		this.leg_back_left.xRot = Mth.cos(state.walkAnimationPos * 0.6662F + (float) Math.PI) * 1.4F * speed;
		this.leg_front_right.xRot = Mth.cos(state.walkAnimationPos * 0.6662F + (float) Math.PI) * 1.4F * speed;
		this.leg_front_left.xRot = Mth.cos(state.walkAnimationPos * 0.6662F) * 1.4F * speed;
		// the wings open as it leaves the ground (flapSpeed: 0 on the ground, 1 in the air) and beat while it is up
		float open = Mth.clamp(state.flapSpeed, 0.0F, 1.0F);
		this.openWingAnimation.applyWalk(0.0F, open, 1.0F, 1.0F);
		float beat = Mth.sin(state.flap) * open * WING_BEAT;
		this.wing_left.zRot -= beat;
		this.wing_right.zRot += beat;
		this.saddle.visible = state.saddled;
	}
}
