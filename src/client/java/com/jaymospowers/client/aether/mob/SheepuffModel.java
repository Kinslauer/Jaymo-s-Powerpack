/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/SheepuffModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, posed from our own render state.
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.mob;


import com.jaymospowers.client.aether.anim.SheepuffAnimations;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

final class SheepuffModel extends AetherAnimalModel {
	public float headXRot;

	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart ear_left;
	private final ModelPart ear_right;
	private final ModelPart leg_front_right;
	private final ModelPart wool_leg_front_right;
	private final ModelPart leg_front_left;
	private final ModelPart wool_leg_front_left;
	private final ModelPart leg_back_left;
	private final ModelPart wool_leg_back_left;
	private final ModelPart leg_back_right;
	private final ModelPart wool_leg_back_right;
	private final ModelPart tail;
	private final ModelPart wool;
	public final KeyframeAnimation walkAnimation;
	public final KeyframeAnimation fallingAnimation;
	public final KeyframeAnimation expandAnimation;
	public final KeyframeAnimation babyAnimation;


	private SheepuffModel(ModelPart root) {
		super(root);
		this.root = root;
		this.body = root.getChild("body");
		this.head = this.body.getChild("head");
		this.ear_left = this.head.getChild("ear_left");
		this.ear_right = this.head.getChild("ear_right");
		this.leg_front_right = this.body.getChild("leg_front_right");
		this.wool_leg_front_right = this.leg_front_right.getChild("wool_leg_front_right");
		this.leg_front_left = this.body.getChild("leg_front_left");
		this.wool_leg_front_left = this.leg_front_left.getChild("wool_leg_front_left");
		this.leg_back_left = this.body.getChild("leg_back_left");
		this.wool_leg_back_left = this.leg_back_left.getChild("wool_leg_back_left");
		this.leg_back_right = this.body.getChild("leg_back_right");
		this.wool_leg_back_right = this.leg_back_right.getChild("wool_leg_back_right");
		this.tail = this.body.getChild("tail");
		this.wool = this.body.getChild("wool");
		this.walkAnimation = SheepuffAnimations.walk.bake(root);
		this.fallingAnimation = SheepuffAnimations.falling.bake(root);
		this.expandAnimation = SheepuffAnimations.wool_expand.bake(root);
		this.babyAnimation = SheepuffAnimations.baby.bake(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 14).addBox(-4.5F, -4.0F, -8.0F, 9.0F, 7.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 14.0F, 0.0F));

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -3.0F, -4.0F, 7.0F, 6.0F, 5.0F, new CubeDeformation(0.0F))
				.texOffs(0, 11).addBox(-2.0F, 0.0F, -5.5F, 4.0F, 4.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -3.0F, -9.0F));

		PartDefinition crest_3_r1 = head.addOrReplaceChild("crest_3_r1", CubeListBuilder.create().texOffs(26, 0).addBox(-3.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(32, 0).addBox(1.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(24, 3).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -3.0F, -3.5F, -0.6109F, 0.0F, 0.0F));

		PartDefinition ear_left = head.addOrReplaceChild("ear_left", CubeListBuilder.create(), PartPose.offset(3.5F, 0.0F, -2.5F));

		PartDefinition ear_left_r1 = ear_left.addOrReplaceChild("ear_left_r1", CubeListBuilder.create().texOffs(19, 11).addBox(0.0F, -3.0F, 0.0F, 4.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.5236F, -0.2618F));

		PartDefinition ear_right = head.addOrReplaceChild("ear_right", CubeListBuilder.create(), PartPose.offset(-3.5F, 0.0F, -2.5F));

		PartDefinition ear_right_r1 = ear_right.addOrReplaceChild("ear_right_r1", CubeListBuilder.create().texOffs(11, 11).addBox(-4.0F, -3.0F, 0.0F, 4.0F, 3.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.5236F, 0.2618F));

		PartDefinition leg_front_right = body.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(0, 35).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, 3.0F, -5.0F));

		PartDefinition wool_leg_front_right = leg_front_right.addOrReplaceChild("wool_leg_front_right", CubeListBuilder.create().texOffs(16, 35).addBox(-1.5F, 0.0F, -1.75F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition leg_front_left = body.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(8, 35).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, 3.0F, -5.0F));

		PartDefinition wool_left_front_left = leg_front_left.addOrReplaceChild("wool_leg_front_left", CubeListBuilder.create().texOffs(28, 35).addBox(-1.5F, 0.0F, -1.75F, 3.0F, 3.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition leg_back_left = body.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(8, 45).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, 3.0F, 6.0F));

		PartDefinition wool_leg_back_left = leg_back_left.addOrReplaceChild("wool_leg_back_left", CubeListBuilder.create().texOffs(28, 41).addBox(2.5F, -2.0F, -1.75F, 3.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(-4.0F, 0.0F, 0.0F));

		PartDefinition leg_back_right = body.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(0, 45).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 8.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(-2.0F, 3.0F, 6.0F));

		PartDefinition wool_leg_back_right = leg_back_right.addOrReplaceChild("wool_leg_back_right", CubeListBuilder.create().texOffs(16, 41).addBox(-5.5F, -2.0F, -1.75F, 3.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(4.0F, 0.0F, 0.0F));

		PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create(), PartPose.offset(0.0F, -3.0F, 5.25F));

		PartDefinition tail_r1 = tail.addOrReplaceChild("tail_r1", CubeListBuilder.create().texOffs(32, 20).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.3927F, 0.0F, 0.0F));

		PartDefinition wool = body.addOrReplaceChild("wool", CubeListBuilder.create().texOffs(44, 0).addBox(-5.0F, -7.0F, -8.25F, 10.0F, 11.0F, 10.0F, new CubeDeformation(0.0F))
				.texOffs(49, 21).addBox(-5.0F, -5.0F, 1.75F, 10.0F, 9.0F, 5.0F, new CubeDeformation(0.0F))
				.texOffs(51, 35).addBox(-4.5F, -6.5F, -9.5F, 9.0F, 9.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 64);
	}

	static SheepuffModel create() {
		return new SheepuffModel(createBodyLayer().bakeRoot());
	}


	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		// grazing: the head drops (9 pixels at full) and tips down, chewing
		this.head.y = -3.0F + state.eating * 9.0F;
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
		this.head.xRot = state.eating > 0.0F ? state.eating * (0.9F + Mth.sin(state.ageInTicks * 0.9F) * 0.12F) : state.xRot * Mth.DEG_TO_RAD;
		this.wool.visible = !state.sheared;
		this.wool_leg_front_right.visible = !state.sheared;
		this.wool_leg_front_left.visible = !state.sheared;
		this.wool_leg_back_right.visible = !state.sheared;
		this.wool_leg_back_left.visible = !state.sheared;
		this.walkAnimation.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 2.0F, 2.5F);
		if (state.puffed) {
			if (state.air > 0.0F) {
				this.fallingAnimation.applyStatic();
			}
			this.expandAnimation.applyStatic();
		}
	}
}
