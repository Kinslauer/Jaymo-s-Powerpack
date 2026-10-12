package com.jaymoskingdoms.client.equestrian;

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
 * (0.5.34) The war elephant, an original model (texture 128x128, {@code tools/gen_equestrian.py}). Built at horse size
 * and drawn {@link com.jaymoskingdoms.equestrian.Breed#WAR_ELEPHANT}'s scale bigger. Its trunk sways and its ears
 * flap; rearing, it raises its trunk to trumpet. Saddled, it wears a howdah with a banner, a caparison and a forehead plate.
 */
final class WarElephantModel extends EntityModel<WarElephantRenderer.State> {
	private final ModelPart head, leftEar, rightEar, trunk1, trunk2, trunk3, tail;
	private final ModelPart rightHindLeg, leftHindLeg, rightFrontLeg, leftFrontLeg;
	private final ModelPart howdah, blanket, plate;

	WarElephantModel(ModelPart root) {
		super(root);
		ModelPart body = root.getChild("body");
		this.head = root.getChild("head");
		this.leftEar = this.head.getChild("left_ear");
		this.rightEar = this.head.getChild("right_ear");
		this.trunk1 = this.head.getChild("trunk1");
		this.trunk2 = this.trunk1.getChild("trunk2");
		this.trunk3 = this.trunk2.getChild("trunk3");
		this.plate = this.head.getChild("head_plate");
		this.tail = body.getChild("tail");
		this.howdah = body.getChild("howdah");
		this.blanket = body.getChild("blanket");
		this.rightHindLeg = root.getChild("right_hind_leg");
		this.leftHindLeg = root.getChild("left_hind_leg");
		this.rightFrontLeg = root.getChild("right_front_leg");
		this.leftFrontLeg = root.getChild("left_front_leg");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -6.0F, -11.0F, 14.0F, 13.0F, 22.0F),
			PartPose.offset(0.0F, 8.0F, 2.0F));
		PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(108, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 8.0F, 1.0F),
			PartPose.offsetAndRotation(0.0F, -4.0F, 11.0F, 0.25F, 0.0F, 0.0F));
		tail.addOrReplaceChild("tail_tuft", CubeListBuilder.create().texOffs(108, 10).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F), PartPose.offset(0.0F, 8.0F, 0.0F));
		body.addOrReplaceChild("blanket", CubeListBuilder.create().texOffs(0, 101).addBox(-7.5F, -6.4F, -6.0F, 15.0F, 8.0F, 12.0F), PartPose.ZERO);
		PartDefinition howdah = body.addOrReplaceChild("howdah", CubeListBuilder.create()
			.texOffs(0, 72).addBox(-8.0F, -1.0F, -6.0F, 16.0F, 1.0F, 12.0F)
			.texOffs(0, 86).addBox(-8.0F, -3.0F, -6.0F, 16.0F, 2.0F, 1.0F)
			.texOffs(0, 86).addBox(-8.0F, -3.0F, 5.0F, 16.0F, 2.0F, 1.0F)
			.texOffs(36, 86).addBox(-8.0F, -3.0F, -6.0F, 1.0F, 2.0F, 12.0F)
			.texOffs(36, 86).addBox(7.0F, -3.0F, -6.0F, 1.0F, 2.0F, 12.0F)
			.texOffs(88, 72).addBox(6.5F, -13.0F, 4.5F, 1.0F, 10.0F, 1.0F)
			.texOffs(94, 72).addBox(7.0F, -13.0F, 5.5F, 0.0F, 6.0F, 5.0F),
			PartPose.offset(0.0F, -6.5F, 0.0F));
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 36).addBox(-5.0F, -4.0F, -8.0F, 10.0F, 10.0F, 8.0F),
			PartPose.offset(0.0F, 4.0F, -9.0F));
		head.addOrReplaceChild("head_plate", CubeListBuilder.create().texOffs(60, 72).addBox(-5.5F, -4.5F, -8.6F, 11.0F, 6.0F, 1.0F), PartPose.ZERO);
		head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(40, 36).addBox(0.0F, 0.0F, -0.5F, 7.0F, 9.0F, 1.0F),
			PartPose.offsetAndRotation(5.0F, -3.0F, -3.0F, 0.0F, -0.35F, 0.0F));
		head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(58, 36).addBox(-7.0F, 0.0F, -0.5F, 7.0F, 9.0F, 1.0F),
			PartPose.offsetAndRotation(-5.0F, -3.0F, -3.0F, 0.0F, 0.35F, 0.0F));
		PartDefinition trunk1 = head.addOrReplaceChild("trunk1", CubeListBuilder.create().texOffs(76, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F),
			PartPose.offsetAndRotation(0.0F, 4.0F, -7.0F, -0.1F, 0.0F, 0.0F));
		PartDefinition trunk2 = trunk1.addOrReplaceChild("trunk2", CubeListBuilder.create().texOffs(76, 10).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 5.0F, 3.0F),
			PartPose.offsetAndRotation(0.0F, 4.8F, 0.0F, -0.15F, 0.0F, 0.0F));
		trunk2.addOrReplaceChild("trunk3", CubeListBuilder.create().texOffs(76, 19).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
			PartPose.offsetAndRotation(0.0F, 4.8F, 0.0F, -0.25F, 0.0F, 0.0F));
		head.addOrReplaceChild("left_tusk", CubeListBuilder.create().texOffs(92, 0).addBox(-0.5F, -0.5F, -6.0F, 1.0F, 1.0F, 6.0F),
			PartPose.offsetAndRotation(3.0F, 5.0F, -6.5F, -0.35F, 0.0F, 0.0F));
		head.addOrReplaceChild("right_tusk", CubeListBuilder.create().texOffs(92, 0).mirror().addBox(-0.5F, -0.5F, -6.0F, 1.0F, 1.0F, 6.0F),
			PartPose.offsetAndRotation(-3.0F, 5.0F, -6.5F, -0.35F, 0.0F, 0.0F));
		CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 56).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 9.0F, 5.0F);
		CubeListBuilder mirrored = CubeListBuilder.create().texOffs(0, 56).mirror().addBox(-2.5F, 0.0F, -2.5F, 5.0F, 9.0F, 5.0F);
		root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-4.0F, 15.0F, 9.0F));
		root.addOrReplaceChild("left_hind_leg", mirrored, PartPose.offset(4.0F, 15.0F, 9.0F));
		root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-4.0F, 15.0F, -5.0F));
		root.addOrReplaceChild("left_front_leg", mirrored, PartPose.offset(4.0F, 15.0F, -5.0F));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(WarElephantRenderer.State state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos;
		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		float swing = Mth.cos(pos * 0.6662F) * 0.7F * speed;
		this.rightHindLeg.xRot = swing;
		this.leftFrontLeg.xRot = swing;
		this.leftHindLeg.xRot = -swing;
		this.rightFrontLeg.xRot = -swing;
		float age = state.ageInTicks;
		float stand = state.standAnimation;
		this.head.yRot = Mth.clamp(state.yRot, -20.0F, 20.0F) * Mth.DEG_TO_RAD;
		this.head.xRot = Mth.clamp(state.xRot, -15.0F, 25.0F) * Mth.DEG_TO_RAD * 0.5F - stand * 0.35F + Mth.cos(pos * 0.6662F * 2.0F) * 0.03F * speed;
		// the trunk sways, curling up to trumpet
		this.trunk1.zRot = Mth.sin(age * 0.05F) * 0.08F;
		this.trunk1.xRot = Mth.lerp(stand, -0.1F + Mth.sin(age * 0.07F) * 0.05F, -1.7F);
		this.trunk2.xRot = Mth.lerp(stand, -0.15F, -0.9F);
		this.trunk3.xRot = Mth.lerp(stand, -0.25F, -0.9F);
		float flap = Mth.sin(age * 0.08F) * 0.15F + stand * 0.5F;
		this.leftEar.yRot = -0.35F - flap;
		this.rightEar.yRot = 0.35F + flap;
		this.tail.zRot = Mth.sin(age * 0.1F) * 0.15F;
		this.tail.xRot = 0.25F + speed * 0.4F;
		this.howdah.visible = state.saddled;
		this.blanket.visible = state.saddled;
		this.plate.visible = state.saddled;
	}
}
