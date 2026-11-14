/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/PrismallardModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, posed from our own render state.
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.mob;


import com.jaymospowers.client.aether.anim.FlyingCowAnimation;
import com.jaymospowers.client.aether.anim.PrismallardAnimations;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

final class PrismallardModel extends AetherAnimalModel {
	private final ModelPart body;
	private final ModelPart neck;
	private final ModelPart head;
	private final ModelPart jaw;
	private final ModelPart head_detail;
	private final ModelPart left_leg;
	private final ModelPart right_leg;
	private final ModelPart tail_feathers;
	private final ModelPart left_tail;
	private final ModelPart left_centre_tail;
	private final ModelPart left_medium_tail;
	private final ModelPart left_outer_tail;
	private final ModelPart right_tail;
	private final ModelPart right_centre_tail;
	private final ModelPart right_medium_tail;
	private final ModelPart right_outer_tail;
	private final ModelPart wing_left;
	private final ModelPart wing_right;
	private final KeyframeAnimation flapAnimation;
	private final KeyframeAnimation displayAnimation;
	private final KeyframeAnimation babyAnimation;

	private PrismallardModel(ModelPart root) {
		super(root);

		this.flapAnimation = PrismallardAnimations.flap.bake(root);
		this.displayAnimation = PrismallardAnimations.display.bake(root);
		this.babyAnimation = FlyingCowAnimation.BABY.bake(root);
		this.body = root.getChild("body");
		this.neck = this.body.getChild("neck");
		this.head = this.neck.getChild("head");
		this.jaw = this.head.getChild("jaw");
		this.head_detail = this.head.getChild("head_detail");
		this.left_leg = this.body.getChild("left_leg");
		this.right_leg = this.body.getChild("right_leg");
		this.tail_feathers = this.body.getChild("tail_feathers");
		this.left_tail = this.tail_feathers.getChild("left_tail");
		this.left_centre_tail = this.left_tail.getChild("left_centre_tail");
		this.left_medium_tail = this.left_tail.getChild("left_medium_tail");
		this.left_outer_tail = this.left_tail.getChild("left_outer_tail");
		this.right_tail = this.tail_feathers.getChild("right_tail");
		this.right_centre_tail = this.right_tail.getChild("right_centre_tail");
		this.right_medium_tail = this.right_tail.getChild("right_medium_tail");
		this.right_outer_tail = this.right_tail.getChild("right_outer_tail");
		this.wing_left = this.body.getChild("wing_left");
		this.wing_right = this.body.getChild("wing_right");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(1, 1).addBox(-2.0F, -3.5F, -2.5F, 5.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.5F, 19.0F, 0.0F));

		PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(26, 4).addBox(-1.5F, -2.5F, -1.5F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, -1.5F, -3.0F));

		PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 12).addBox(-2.5F, -3.0F, -2.5F, 5.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(17, 0).addBox(-2.5F, -1.0F, -5.5F, 5.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -3.5F, -1.0F));

		PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(28, 13).addBox(-1.5F, -0.25F, -2.5F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -2.5F));

		PartDefinition head_detail = head.addOrReplaceChild("head_detail", CubeListBuilder.create().texOffs(34, 0).addBox(0.0F, 0.0F, -0.5F, 1.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
				.texOffs(14, 16).addBox(-2.5F, 0.0F, 0.0F, 5.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -3.0F, 1.5F, 0.1309F, 0.0F, 0.0F));

		PartDefinition left_leg = body.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(24, 21).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, 1.5F, 1.5F));

		PartDefinition right_leg = body.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(12, 21).addBox(-1.5F, -0.5F, -3.0F, 3.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-1.0F, 1.5F, 1.5F));

		PartDefinition tail_feathers = body.addOrReplaceChild("tail_feathers", CubeListBuilder.create(), PartPose.offset(0.5F, -3.0F, 2.5F));

		PartDefinition left_tail = tail_feathers.addOrReplaceChild("left_tail", CubeListBuilder.create(), PartPose.offset(1.0F, 0.0F, 0.0F));

		PartDefinition left_centre_tail = left_tail.addOrReplaceChild("left_centre_tail", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition left_centre_tail_r1 = left_centre_tail.addOrReplaceChild("left_centre_tail_r1", CubeListBuilder.create().texOffs(17, 28).addBox(-1.0F, -11.0F, 0.0F, 2.0F, 11.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 0.0436F, 0.0873F));

		PartDefinition left_medium_tail = left_tail.addOrReplaceChild("left_medium_tail", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition left_medium_tail_r1 = left_medium_tail.addOrReplaceChild("left_medium_tail_r1", CubeListBuilder.create().texOffs(0, 29).addBox(-0.75F, -9.0F, 0.25F, 2.0F, 9.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 0.0873F, 0.3927F));

		PartDefinition left_outer_tail = left_tail.addOrReplaceChild("left_outer_tail", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition left_outer_tail_r1 = left_outer_tail.addOrReplaceChild("left_outer_tail_r1", CubeListBuilder.create().texOffs(35, 27).addBox(-0.5F, -7.0F, 0.5F, 2.0F, 7.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, 0.1309F, 0.8727F));

		PartDefinition right_tail = tail_feathers.addOrReplaceChild("right_tail", CubeListBuilder.create(), PartPose.offset(-1.0F, 0.0F, 0.0F));

		PartDefinition right_centre_tail = right_tail.addOrReplaceChild("right_centre_tail", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition right_centre_tail_r1 = right_centre_tail.addOrReplaceChild("right_centre_tail_r1", CubeListBuilder.create().texOffs(11, 28).addBox(-1.0F, -11.0F, 0.0F, 2.0F, 11.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, -0.0436F, -0.0873F));

		PartDefinition right_medium_tail = right_tail.addOrReplaceChild("right_medium_tail", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition right_medium_tail_r1 = right_medium_tail.addOrReplaceChild("right_medium_tail_r1", CubeListBuilder.create().texOffs(23, 28).addBox(-1.25F, -9.0F, 0.25F, 2.0F, 9.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, -0.0873F, -0.3927F));

		PartDefinition right_outer_tail = right_tail.addOrReplaceChild("right_outer_tail", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition right_outer_tail_r1 = right_outer_tail.addOrReplaceChild("right_outer_tail_r1", CubeListBuilder.create().texOffs(29, 28).addBox(-1.5F, -7.0F, 0.5F, 2.0F, 7.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -1.5708F, -0.1309F, -0.8727F));

		PartDefinition wing_left = body.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(0, 20).addBox(-0.4132F, -2.06F, -0.4229F, 1.0F, 4.0F, 5.0F, new CubeDeformation(0.0F))
				.texOffs(33, 7).addBox(-0.4132F, -2.06F, 4.5771F, 1.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, -1.25F, -1.0F, 0.1309F, 0.1745F, 0.0F));

		PartDefinition wing_right = body.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(19, 7).addBox(-0.7605F, -2.195F, -0.4972F, 1.0F, 4.0F, 5.0F, new CubeDeformation(0.0F))
				.texOffs(33, 17).addBox(-0.7605F, -2.195F, 4.5028F, 1.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.0F, -1.25F, -1.0F, 0.1309F, -0.1745F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	static PrismallardModel create() {
		return new PrismallardModel(createBodyLayer().bakeRoot());
	}


	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		this.head.xRot = state.xRot * (float) (Math.PI / 180.0);
		this.head.yRot = state.yRot * (float) (Math.PI / 180.0);
		float flapAngle = (Mth.sin(state.flap) + 1.0F) * state.flapSpeed;
		float animationSpeed = Math.min(state.walkAnimationSpeed, 1.0F);
		float animationPos = state.walkAnimationPos;
		this.right_leg.xRot = Mth.cos(animationPos * 0.6662F) * 1.4F * animationSpeed;
		this.left_leg.xRot = Mth.cos(animationPos * 0.6662F + (float) Math.PI) * 1.4F * animationSpeed;
		this.wing_right.zRot = flapAngle;
		this.wing_left.zRot = -flapAngle;
		this.flapAnimation.applyWalk(state.ageInTicks, state.flapSpeed, 1.0F, 1.0F);
	}
}
