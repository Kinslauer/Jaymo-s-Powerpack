package com.jaymoskingdoms.client.monsters;

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
 * The Nether scorpion, from {@code MoCModelScorpion} of DrZhark's Mo' Creatures (GPL-3, see {@code licenses/mocreatures}),
 * ported to 26.3: its boxes and its animation as they were (eight legs walking in pairs, the claws opening and closing
 * now and then and striking in turn on an attack, the tail up over the back, the brood on a mother's back wriggling).
 */
final class ScorpionModel extends EntityModel<BeastState> {
	private static final float RAD = 57.29578F;
	private final ModelPart Head, MouthL, MouthR, Body, Tail1, Tail2, Tail3, Tail4, Tail5, Sting1, Sting2, LArm1, LArm2, LArm3, LArm4, RArm1, RArm2, RArm3, RArm4, Leg1A, Leg1B, Leg1C, Leg2A, Leg2B, Leg2C, Leg3A, Leg3B, Leg3C, Leg4A, Leg4B, Leg4C, Leg5A, Leg5B, Leg5C, Leg6A, Leg6B, Leg6C, Leg7A, Leg7B, Leg7C, Leg8A, Leg8B, Leg8C, baby1, baby2, baby3, baby4, baby5;
	private boolean poisoning, isTalking, babies, sitting;
	private int attacking;

	ScorpionModel(ModelPart root) {
		super(root);
		this.Head = root.getChild("Head");
		this.MouthL = root.getChild("MouthL");
		this.MouthR = root.getChild("MouthR");
		this.Body = root.getChild("Body");
		this.Tail1 = root.getChild("Tail1");
		this.Tail2 = root.getChild("Tail2");
		this.Tail3 = root.getChild("Tail3");
		this.Tail4 = root.getChild("Tail4");
		this.Tail5 = root.getChild("Tail5");
		this.Sting1 = root.getChild("Sting1");
		this.Sting2 = root.getChild("Sting2");
		this.LArm1 = root.getChild("LArm1");
		this.LArm2 = root.getChild("LArm2");
		this.LArm3 = root.getChild("LArm3");
		this.LArm4 = root.getChild("LArm4");
		this.RArm1 = root.getChild("RArm1");
		this.RArm2 = root.getChild("RArm2");
		this.RArm3 = root.getChild("RArm3");
		this.RArm4 = root.getChild("RArm4");
		this.Leg1A = root.getChild("Leg1A");
		this.Leg1B = root.getChild("Leg1B");
		this.Leg1C = root.getChild("Leg1C");
		this.Leg2A = root.getChild("Leg2A");
		this.Leg2B = root.getChild("Leg2B");
		this.Leg2C = root.getChild("Leg2C");
		this.Leg3A = root.getChild("Leg3A");
		this.Leg3B = root.getChild("Leg3B");
		this.Leg3C = root.getChild("Leg3C");
		this.Leg4A = root.getChild("Leg4A");
		this.Leg4B = root.getChild("Leg4B");
		this.Leg4C = root.getChild("Leg4C");
		this.Leg5A = root.getChild("Leg5A");
		this.Leg5B = root.getChild("Leg5B");
		this.Leg5C = root.getChild("Leg5C");
		this.Leg6A = root.getChild("Leg6A");
		this.Leg6B = root.getChild("Leg6B");
		this.Leg6C = root.getChild("Leg6C");
		this.Leg7A = root.getChild("Leg7A");
		this.Leg7B = root.getChild("Leg7B");
		this.Leg7C = root.getChild("Leg7C");
		this.Leg8A = root.getChild("Leg8A");
		this.Leg8B = root.getChild("Leg8B");
		this.Leg8C = root.getChild("Leg8C");
		this.baby1 = root.getChild("baby1");
		this.baby2 = root.getChild("baby2");
		this.baby3 = root.getChild("baby3");
		this.baby4 = root.getChild("baby4");
		this.baby5 = root.getChild("baby5");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_Head = root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-5F, 0F, 0F, 10F, 5F, 13F), PartPose.offset(0F, 14F, -9F));
		PartDefinition p_MouthL = root.addOrReplaceChild("MouthL", CubeListBuilder.create().texOffs(18, 58).mirror(false).addBox(-3F, -2F, -1F, 4F, 4F, 2F), PartPose.offsetAndRotation(3F, 17F, -9F, 0F, -0.3839724F, 0F));
		PartDefinition p_MouthR = root.addOrReplaceChild("MouthR", CubeListBuilder.create().texOffs(30, 58).mirror(false).addBox(-1F, -2F, -1F, 4F, 4F, 2F), PartPose.offsetAndRotation(-3F, 17F, -9F, 0F, 0.3839724F, 0F));
		PartDefinition p_Body = root.addOrReplaceChild("Body", CubeListBuilder.create().texOffs(0, 18).mirror(false).addBox(-4F, -2F, 0F, 8F, 4F, 10F), PartPose.offsetAndRotation(0F, 17F, 3F, 0.0872665F, 0F, 0F));
		PartDefinition p_Tail1 = root.addOrReplaceChild("Tail1", CubeListBuilder.create().texOffs(0, 32).mirror(false).addBox(-3F, -2F, 0F, 6F, 4F, 6F), PartPose.offsetAndRotation(0F, 16F, 12F, 0.6108652F, 0F, 0F));
		PartDefinition p_Tail2 = root.addOrReplaceChild("Tail2", CubeListBuilder.create().texOffs(0, 42).mirror(false).addBox(-2F, -2F, 0F, 4F, 4F, 6F), PartPose.offsetAndRotation(0F, 13F, 16.5F, 1.134464F, 0F, 0F));
		PartDefinition p_Tail3 = root.addOrReplaceChild("Tail3", CubeListBuilder.create().texOffs(0, 52).mirror(false).addBox(-1.5F, -1.5F, 0F, 3F, 3F, 6F), PartPose.offsetAndRotation(0F, 8F, 18.5F, 1.692143F, 0F, 0F));
		PartDefinition p_Tail4 = root.addOrReplaceChild("Tail4", CubeListBuilder.create().texOffs(24, 32).mirror(false).addBox(-1.5F, -1.5F, 0F, 3F, 3F, 6F), PartPose.offsetAndRotation(0F, 3F, 18F, 2.510073F, 0F, 0F));
		PartDefinition p_Tail5 = root.addOrReplaceChild("Tail5", CubeListBuilder.create().texOffs(24, 41).mirror(false).addBox(-1.5F, -1.5F, 0F, 3F, 3F, 6F), PartPose.offsetAndRotation(0F, -0.2F, 14F, 3.067752F, 0F, 0F));
		PartDefinition p_Sting1 = root.addOrReplaceChild("Sting1", CubeListBuilder.create().texOffs(30, 50).mirror(false).addBox(-1.5F, 0F, -1.5F, 3F, 5F, 3F), PartPose.offsetAndRotation(0F, -1F, 7F, 0.4089647F, 0F, 0F));
		PartDefinition p_Sting2 = root.addOrReplaceChild("Sting2", CubeListBuilder.create().texOffs(26, 50).mirror(false).addBox(-0.5F, 0F, 0.5F, 1F, 4F, 1F), PartPose.offsetAndRotation(0F, 2.6F, 8.8F, -0.2230717F, 0F, 0F));
		PartDefinition p_LArm1 = root.addOrReplaceChild("LArm1", CubeListBuilder.create().texOffs(26, 18).mirror(false).addBox(-1F, -7F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(5F, 18F, -8F, -0.3490659F, 0F, 0.8726646F));
		PartDefinition p_LArm2 = root.addOrReplaceChild("LArm2", CubeListBuilder.create().texOffs(42, 55).mirror(false).addBox(-1.5F, -1.5F, -6F, 3F, 3F, 6F), PartPose.offsetAndRotation(10F, 14F, -6F, 0.1745329F, -0.3490659F, -0.2617994F));
		PartDefinition p_LArm3 = root.addOrReplaceChild("LArm3", CubeListBuilder.create().texOffs(42, 39).mirror(false).addBox(-0.5F, -0.5F, -7F, 2F, 1F, 7F), PartPose.offsetAndRotation(12F, 15F, -11F, 0.2617994F, 0.1570796F, -0.1570796F));
		PartDefinition p_LArm4 = root.addOrReplaceChild("LArm4", CubeListBuilder.create().texOffs(42, 31).mirror(false).addBox(-1.5F, -0.5F, -6F, 1F, 1F, 7F), PartPose.offsetAndRotation(11F, 15F, -11F, 0.2617994F, 0F, -0.1570796F));
		PartDefinition p_RArm1 = root.addOrReplaceChild("RArm1", CubeListBuilder.create().texOffs(0, 18).mirror(false).addBox(-1F, -7F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(-5F, 18F, -8F, -0.3490659F, 0F, -0.8726646F));
		PartDefinition p_RArm2 = root.addOrReplaceChild("RArm2", CubeListBuilder.create().texOffs(42, 55).mirror(false).addBox(-1.5F, -1.5F, -6F, 3F, 3F, 6F), PartPose.offsetAndRotation(-10F, 14F, -6F, 0.1745329F, 0.3490659F, 0.2617994F));
		PartDefinition p_RArm3 = root.addOrReplaceChild("RArm3", CubeListBuilder.create().texOffs(42, 47).mirror(false).addBox(-1.5F, -0.5F, -7F, 2F, 1F, 7F), PartPose.offsetAndRotation(-12F, 15F, -11F, 0.2617994F, -0.1570796F, 0.1570796F));
		PartDefinition p_RArm4 = root.addOrReplaceChild("RArm4", CubeListBuilder.create().texOffs(42, 31).mirror(false).addBox(0.5F, -0.5F, -6F, 1F, 1F, 7F), PartPose.offsetAndRotation(-11F, 15F, -11F, 0.2617994F, 0F, 0.1570796F));
		PartDefinition p_Leg1A = root.addOrReplaceChild("Leg1A", CubeListBuilder.create().texOffs(38, 0).mirror(false).addBox(-1F, -7F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(5F, 18F, -5F, -0.1745329F, 0F, 1.3089969F));
		PartDefinition p_Leg1B = root.addOrReplaceChild("Leg1B", CubeListBuilder.create().texOffs(50, 0).mirror(false).addBox(2F, -8F, -1F, 5F, 2F, 2F), PartPose.offsetAndRotation(5F, 18F, -5F, -0.1745329F, 0F, 1.0471975F));
		PartDefinition p_Leg1C = root.addOrReplaceChild("Leg1C", CubeListBuilder.create().texOffs(52, 16).mirror(false).addBox(4.5F, -9F, -0.7F, 5F, 1F, 1F), PartPose.offsetAndRotation(5F, 18F, -5F, -0.1745329F, 0F, 1.3089969F));
		PartDefinition p_Leg2A = root.addOrReplaceChild("Leg2A", CubeListBuilder.create().texOffs(38, 0).mirror(false).addBox(-1F, -7F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(5F, 18F, -2F, -0.5235988F, 0F, 1.2217305F));
		PartDefinition p_Leg2B = root.addOrReplaceChild("Leg2B", CubeListBuilder.create().texOffs(50, 4).mirror(false).addBox(1F, -8F, -1F, 5F, 2F, 2F), PartPose.offsetAndRotation(5F, 18F, -2F, -0.5235988F, 0F, 1.0471975F));
		PartDefinition p_Leg2C = root.addOrReplaceChild("Leg2C", CubeListBuilder.create().texOffs(50, 18).mirror(false).addBox(4F, -8.5F, -1F, 6F, 1F, 1F), PartPose.offsetAndRotation(5F, 18F, -2F, -0.5235988F, 0F, 1.2217305F));
		PartDefinition p_Leg3A = root.addOrReplaceChild("Leg3A", CubeListBuilder.create().texOffs(38, 0).mirror(false).addBox(-1F, -7F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(5F, 17.5F, 1F, -0.7853982F, 0F, 1.2217305F));
		PartDefinition p_Leg3B = root.addOrReplaceChild("Leg3B", CubeListBuilder.create().texOffs(48, 8).mirror(false).addBox(1F, -8F, -1F, 6F, 2F, 2F), PartPose.offsetAndRotation(5F, 17.5F, 1F, -0.7853982F, 0F, 1.0471975F));
		PartDefinition p_Leg3C = root.addOrReplaceChild("Leg3C", CubeListBuilder.create().texOffs(50, 20).mirror(false).addBox(4.5F, -8.2F, -1.3F, 6F, 1F, 1F), PartPose.offsetAndRotation(5F, 17.5F, 1F, -0.7853982F, 0F, 1.2217305F));
		PartDefinition p_Leg4A = root.addOrReplaceChild("Leg4A", CubeListBuilder.create().texOffs(38, 0).mirror(false).addBox(-1F, -7F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(5F, 17F, 4F, -1.0471975F, 0F, 1.2217305F));
		PartDefinition p_Leg4B = root.addOrReplaceChild("Leg4B", CubeListBuilder.create().texOffs(46, 12).mirror(false).addBox(0.5F, -8.5F, -1F, 7F, 2F, 2F), PartPose.offsetAndRotation(5F, 17F, 4F, -1.0471975F, 0F, 1.0471975F));
		PartDefinition p_Leg4C = root.addOrReplaceChild("Leg4C", CubeListBuilder.create().texOffs(48, 22).mirror(false).addBox(3.5F, -8.5F, -1.5F, 7F, 1F, 1F), PartPose.offsetAndRotation(5F, 17F, 4F, -1.0471975F, 0F, 1.2217305F));
		PartDefinition p_Leg5A = root.addOrReplaceChild("Leg5A", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-1F, -7F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(-5F, 18F, -5F, -0.1745329F, 0F, -1.3089969F));
		PartDefinition p_Leg5B = root.addOrReplaceChild("Leg5B", CubeListBuilder.create().texOffs(50, 0).mirror(false).addBox(-7F, -8F, -1F, 5F, 2F, 2F), PartPose.offsetAndRotation(-5F, 18F, -5F, -0.1745329F, 0F, -1.0471975F));
		PartDefinition p_Leg5C = root.addOrReplaceChild("Leg5C", CubeListBuilder.create().texOffs(52, 16).mirror(false).addBox(-9.5F, -9F, -0.7F, 5F, 1F, 1F), PartPose.offsetAndRotation(-5F, 18F, -5F, -0.1745329F, 0F, -1.3089969F));
		PartDefinition p_Leg6A = root.addOrReplaceChild("Leg6A", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-1F, -7F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(-5F, 18F, -2F, -0.5235988F, 0F, -1.2217305F));
		PartDefinition p_Leg6B = root.addOrReplaceChild("Leg6B", CubeListBuilder.create().texOffs(50, 4).mirror(false).addBox(-6F, -8F, -1F, 5F, 2F, 2F), PartPose.offsetAndRotation(-5F, 18F, -2F, -0.5235988F, 0F, -1.0471975F));
		PartDefinition p_Leg6C = root.addOrReplaceChild("Leg6C", CubeListBuilder.create().texOffs(50, 18).mirror(false).addBox(-10F, -8.5F, -1F, 6F, 1F, 1F), PartPose.offsetAndRotation(-5F, 18F, -2F, -0.5235988F, 0F, -1.0471975F));
		PartDefinition p_Leg7A = root.addOrReplaceChild("Leg7A", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-1F, -7F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(-5F, 17.5F, 1F, -0.7853982F, 0F, -1.2217305F));
		PartDefinition p_Leg7B = root.addOrReplaceChild("Leg7B", CubeListBuilder.create().texOffs(48, 8).mirror(false).addBox(-7F, -8.5F, -1F, 6F, 2F, 2F), PartPose.offsetAndRotation(-5F, 17.5F, 1F, -0.7853982F, 0F, -1.0471975F));
		PartDefinition p_Leg7C = root.addOrReplaceChild("Leg7C", CubeListBuilder.create().texOffs(50, 20).mirror(false).addBox(-10.5F, -8.7F, -1.3F, 6F, 1F, 1F), PartPose.offsetAndRotation(-5F, 17.5F, 1F, -0.7853982F, 0F, -1.2217305F));
		PartDefinition p_Leg8A = root.addOrReplaceChild("Leg8A", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-1F, -7F, -1F, 2F, 7F, 2F), PartPose.offsetAndRotation(-5F, 17F, 4F, -1.0471975F, 0F, -1.2217305F));
		PartDefinition p_Leg8B = root.addOrReplaceChild("Leg8B", CubeListBuilder.create().texOffs(46, 12).mirror(false).addBox(-7.5F, -8.5F, -1F, 7F, 2F, 2F), PartPose.offsetAndRotation(-5F, 17F, 4F, -1.0471975F, 0F, -1.0471975F));
		PartDefinition p_Leg8C = root.addOrReplaceChild("Leg8C", CubeListBuilder.create().texOffs(48, 22).mirror(false).addBox(-10.5F, -8.5F, -1.5F, 7F, 1F, 1F), PartPose.offsetAndRotation(-5F, 17F, 4F, -1.0471975F, 0F, -1.2217305F));
		PartDefinition p_baby1 = root.addOrReplaceChild("baby1", CubeListBuilder.create().texOffs(48, 24).mirror(false).addBox(-1.5F, 0F, -2.5F, 3F, 2F, 5F), PartPose.offset(0F, 12F, 0F));
		PartDefinition p_baby2 = root.addOrReplaceChild("baby2", CubeListBuilder.create().texOffs(48, 24).mirror(false).addBox(-1.5F, 0F, -2.5F, 3F, 2F, 5F), PartPose.offsetAndRotation(-5F, 13.4F, -1F, 0.4461433F, 2.490967F, 0.5205006F));
		PartDefinition p_baby3 = root.addOrReplaceChild("baby3", CubeListBuilder.create().texOffs(48, 24).mirror(false).addBox(-1.5F, 0F, -2.5F, 3F, 2F, 5F), PartPose.offsetAndRotation(-2F, 13F, 4F, 0F, 0.8551081F, 0F));
		PartDefinition p_baby4 = root.addOrReplaceChild("baby4", CubeListBuilder.create().texOffs(48, 24).mirror(false).addBox(-1.5F, 0F, -2.5F, 3F, 2F, 5F), PartPose.offsetAndRotation(4F, 13F, 2F, 0F, 2.714039F, -0.3717861F));
		PartDefinition p_baby5 = root.addOrReplaceChild("baby5", CubeListBuilder.create().texOffs(48, 24).mirror(false).addBox(-1.5F, 0F, -2.5F, 3F, 2F, 5F), PartPose.offsetAndRotation(1F, 13F, 8F, 0F, -1.189716F, 0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(BeastState state) {
		super.setupAnim(state);
		this.poisoning = state.stinging;
		this.babies = state.babies;
		this.attacking = state.clawCounter;
		this.isTalking = state.attackAnim > 0.0F;
		this.baby1.visible = this.baby2.visible = this.baby3.visible = this.baby4.visible = this.baby5.visible = this.babies;
		rig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
	}

	private void rig(float f, float f1, float f2, float f3, float f4) {

        //float RLegXRot = Mth.cos((f * 0.6662F) + 3.141593F) * 0.4F * f1;

        if (!poisoning) {
            this.Body.xRot = 5F / RAD;
            this.Tail1.xRot = 35F / RAD;
            this.Tail1.y = 16F;
            this.Tail1.z = 12F;

            this.Tail2.xRot = 65F / RAD;
            this.Tail2.y = 13F;
            this.Tail2.z = 16.5F;

            this.Tail3.xRot = 90F / RAD;
            this.Tail3.y = 8F;
            this.Tail3.z = 18.5F;

            this.Tail4.xRot = 143F / RAD;
            this.Tail4.y = 3F;
            this.Tail4.z = 18F;

            this.Tail5.xRot = 175F / RAD;
            this.Tail5.y = -0.2F;
            this.Tail5.z = 14F;

            this.Sting1.xRot = 24F / RAD;
            this.Sting1.y = -1F;
            this.Sting1.z = 7F;

            this.Sting2.xRot = -12F / RAD;
            this.Sting2.y = 2.6F;
            this.Sting2.z = 8.8F;

        } else {
            this.Body.xRot = 50F / RAD;
            this.Tail1.xRot = 100F / RAD;
            this.Tail1.y = 9F;
            this.Tail1.z = 10F;

            this.Tail2.xRot = 160F / RAD;
            this.Tail2.y = 3F;
            this.Tail2.z = 9.5F;

            this.Tail3.xRot = -170F / RAD;
            this.Tail3.y = 1F;
            this.Tail3.z = 3.5F;

            this.Tail4.xRot = -156F / RAD;
            this.Tail4.y = 1.8F;
            this.Tail4.z = -2F;

            this.Tail5.xRot = -154F / RAD;
            this.Tail5.y = 3.8F;
            this.Tail5.z = -7F;

            this.Sting1.xRot = -57F / RAD;
            this.Sting1.y = 6F;
            this.Sting1.z = -12F;

            this.Sting2.xRot = -93.7F / RAD;
            this.Sting2.y = 8F;
            this.Sting2.z = -15.2F;
        }

        /**
         * Mouth animation
         */
        float MouthRot = 0F;
        if (isTalking) {
            MouthRot = Mth.cos((f2 * 1.1F)) * 0.2F;
        }
        this.MouthR.yRot = (22F / RAD) + MouthRot;
        this.MouthL.yRot = (-22F / RAD) - MouthRot;

        //Larm normal position
        this.LArm1.xRot = -20F / RAD;
        this.LArm2.x = 10F;
        this.LArm2.y = 14F;
        this.LArm2.z = -6F;
        this.LArm3.x = 12F;
        this.LArm3.y = 15F;
        this.LArm3.z = -11F;
        this.LArm4.x = 11F;
        this.LArm4.y = 15F;
        this.LArm4.z = -11F;
        this.LArm4.yRot = 0F;

        //Rarm normal position
        this.RArm1.xRot = -20F / RAD;
        this.RArm2.x = -10F;
        this.RArm2.y = 14F;
        this.RArm2.z = -6F;
        this.RArm3.x = -12F;
        this.RArm3.y = 15F;
        this.RArm3.z = -11F;
        this.RArm4.x = -11F;
        this.RArm4.y = 15F;
        this.RArm4.z = -11F;
        this.RArm4.yRot = 0F;

        /**
         * Random hand animations
         */
        if (attacking == 0) {

            /**
             * LHand random animation
             */
            float lHand = 0F;

            float f2a = f2 % 100F;
            if (f2a > 0 & f2a < 20) {
                lHand = f2a / RAD;
            }
            this.LArm3.yRot = (9F / RAD) - lHand;
            this.LArm4.yRot = +lHand;

            /**
             * RHand random animation
             */
            float RHand = 0F;
            float f2b = f2 % 75F;
            if (f2b > 30 & f2b < 50) {
                RHand = (f2b - 29) / RAD;
            }
            this.RArm3.yRot = (-9F / RAD) + RHand;
            this.RArm4.yRot = -RHand;
        } else
        //hand attacking sequence
        {
            if (attacking > 0 && attacking < 5) {
                //LArm ahead open
                this.LArm1.xRot = 50F / RAD;
                this.LArm2.x = 8F;
                this.LArm2.y = 15F;
                this.LArm2.z = -13F;
                this.LArm3.x = 10F;
                this.LArm3.y = 16F;
                this.LArm3.z = -18F;
                this.LArm4.x = 9F;
                this.LArm4.y = 16F;
                this.LArm4.z = -18F;
                this.LArm4.yRot = 40F / RAD;
            }

            if (attacking >= 5 && attacking < 10) {
                //LArm ahead closed
                this.LArm1.xRot = 70F / RAD;
                this.LArm2.x = 7F;
                this.LArm2.y = 16F;
                this.LArm2.z = -14F;
                this.LArm3.x = 9F;
                this.LArm3.y = 17F;
                this.LArm3.z = -19F;
                this.LArm4.x = 8F;
                this.LArm4.y = 17F;
                this.LArm4.z = -19F;
                this.LArm4.yRot = 0F;
            }
            if (attacking >= 10 && attacking < 15) {
                //Rarm ahead open
                this.RArm1.xRot = 50F / RAD;
                this.RArm2.x = -8F;
                this.RArm2.y = 15F;
                this.RArm2.z = -13F;
                this.RArm3.x = -10F;
                this.RArm3.y = 16F;
                this.RArm3.z = -18F;
                this.RArm4.x = -9F;
                this.RArm4.y = 16F;
                this.RArm4.z = -18F;
                this.RArm4.yRot = -40F / RAD;
            }
            if (attacking >= 15 && attacking < 20) {
                //RArm ahead closed
                this.RArm1.xRot = 70F / RAD;
                this.RArm2.x = -7F;
                this.RArm2.y = 16F;
                this.RArm2.z = -14F;
                this.RArm3.x = -9F;
                this.RArm3.y = 17F;
                this.RArm3.z = -19F;
                this.RArm4.x = -8F;
                this.RArm4.y = 17F;
                this.RArm4.z = -19F;
                this.RArm4.yRot = 0F;
            }
        }

        /**
         * Babies animation
         */
        if (babies) {
            float fmov = f2 % 100;
            float fb1 = 0F;
            float fb2 = 142F / RAD;
            float fb3 = 49F / RAD;
            float fb4 = 155F / RAD;
            float fb5 = -68F / RAD;

            //fb2 -= fmov;
            //fb5 += fmov;
            //fb1 += (fmov/2F);
            //fb3 -= (fmov*2F);
            //fb4 -= fmov;

            if (fmov > 0F && fmov < 20F) {
                fb2 -= (Mth.cos((f2 * 0.8F)) * 0.3F);
                fb3 -= (Mth.cos((f2 * 0.6F)) * 0.2F);
                fb1 += (Mth.cos((f2 * 0.4F)) * 0.4F);
                fb5 += (Mth.cos((f2 * 0.7F)) * 0.5F);
            }

            if (fmov > 30F && fmov < 50F) {
                fb4 -= (Mth.cos((f2 * 0.8F)) * 0.4F);
                fb1 += (Mth.cos((f2 * 0.7F)) * 0.1F);
                fb3 -= (Mth.cos((f2 * 0.6F)) * 0.2F);
            }
            if (fmov > 80F) {
                fb5 += (Mth.cos((f2 * 0.2F)) * 0.4F);
                fb2 -= (Mth.cos((f2 * 0.6F)) * 0.3F);
                fb4 -= (Mth.cos((f2 * 0.4F)) * 0.2F);
            }

            /*
             * if (fmov>0F && fmov < 30F) { fb2 -= fmov; fb5 += fmov; } if (fmov
             * > 30F && fmov <70F) { fb1 += (fmov/2F); fb3 -= (fmov*2F); } if
             * (fmov >70F) { fb4 -= fmov; }
             */
            this.baby1.yRot = fb1;///RAD;
            this.baby2.yRot = fb2;///RAD;
            this.baby3.yRot = fb3;///RAD;
            this.baby4.yRot = fb4;///RAD;
            this.baby5.yRot = fb5;///RAD;
        }

        /**
         * floats used for the scorpion's leg animations
         */
        float f9 = -(Mth.cos(f * 0.6662F * 2.0F + 0.0F) * 0.4F) * f1;
        float f10 = -(Mth.cos(f * 0.6662F * 2.0F + 3.141593F) * 0.4F) * f1;
        float f11 = -(Mth.cos(f * 0.6662F * 2.0F + 1.570796F) * 0.4F) * f1;
        float f12 = -(Mth.cos(f * 0.6662F * 2.0F + 4.712389F) * 0.4F) * f1;
        float f13 = Math.abs(Mth.sin(f * 0.6662F + 0.0F) * 0.4F) * f1;
        float f14 = Math.abs(Mth.sin(f * 0.6662F + 3.141593F) * 0.4F) * f1;
        float f15 = Math.abs(Mth.sin(f * 0.6662F + 1.570796F) * 0.4F) * f1;
        float f16 = Math.abs(Mth.sin(f * 0.6662F + 4.712389F) * 0.4F) * f1;

        if (sitting) {
            this.Leg1A.xRot = -10F / RAD;
            this.Leg1A.zRot = 35F / RAD;
            this.Leg1B.zRot = 20F / RAD;
            this.Leg1C.zRot = 35F / RAD;

            this.Leg2A.xRot = -30F / RAD;
            this.Leg2A.zRot = 35F / RAD;
            this.Leg2B.zRot = 20F / RAD;
            this.Leg2C.zRot = 35F / RAD;

            this.Leg3A.xRot = -45F / RAD;
            this.Leg3A.zRot = 35F / RAD;
            this.Leg3B.zRot = 20F / RAD;
            this.Leg3C.zRot = 35F / RAD;

            this.Leg4A.xRot = -60F / RAD;
            this.Leg4A.zRot = 35F / RAD;
            this.Leg4B.zRot = 20F / RAD;
            this.Leg4C.zRot = 35F / RAD;

            this.Leg5A.xRot = -10F / RAD;
            this.Leg5A.zRot = -35F / RAD;
            this.Leg5B.zRot = -20F / RAD;
            this.Leg5C.zRot = -35F / RAD;

            this.Leg6A.xRot = -30F / RAD;
            this.Leg6A.zRot = -35F / RAD;
            this.Leg6B.zRot = -20F / RAD;
            this.Leg6C.zRot = -35F / RAD;

            this.Leg7A.xRot = -45F / RAD;
            this.Leg7A.zRot = -35F / RAD;
            this.Leg7B.zRot = -20F / RAD;
            this.Leg7C.zRot = -35F / RAD;

            this.Leg8A.xRot = -60F / RAD;
            this.Leg8A.zRot = -35F / RAD;
            this.Leg8B.zRot = -20F / RAD;
            this.Leg8C.zRot = -35F / RAD;

        } else {
            this.Leg1A.xRot = -10F / RAD;
            this.Leg1A.zRot = 75F / RAD;
            this.Leg1B.zRot = 60F / RAD;
            this.Leg1C.zRot = 75F / RAD;

            this.Leg1A.xRot += f9;
            this.Leg1B.xRot = this.Leg1A.xRot;
            this.Leg1C.xRot = this.Leg1A.xRot;

            this.Leg1A.zRot += f13;
            this.Leg1B.zRot += f13;
            this.Leg1C.zRot += f13;

            this.Leg2A.xRot = -30F / RAD;
            this.Leg2A.zRot = 70F / RAD;
            this.Leg2B.zRot = 60F / RAD;
            this.Leg2C.zRot = 70F / RAD;

            this.Leg2A.xRot += f10;
            this.Leg2B.xRot = this.Leg2A.xRot;
            this.Leg2C.xRot = this.Leg2A.xRot;

            this.Leg2A.zRot += f14;
            this.Leg2B.zRot += f14;
            this.Leg2C.zRot += f14;

            this.Leg3A.xRot = -45F / RAD;
            this.Leg3A.zRot = 70F / RAD;
            this.Leg3B.zRot = 60F / RAD;
            this.Leg3C.zRot = 70F / RAD;

            this.Leg3A.xRot += f11;
            this.Leg3B.xRot = this.Leg3A.xRot;
            this.Leg3C.xRot = this.Leg3A.xRot;

            this.Leg3A.zRot += f15;
            this.Leg3B.zRot += f15;
            this.Leg3C.zRot += f15;

            this.Leg4A.xRot = -60F / RAD;
            this.Leg4A.zRot = 70F / RAD;
            this.Leg4B.zRot = 60F / RAD;
            this.Leg4C.zRot = 70F / RAD;

            this.Leg4A.xRot += f12;
            this.Leg4B.xRot = this.Leg4A.xRot;
            this.Leg4C.xRot = this.Leg4A.xRot;

            this.Leg4A.zRot += f16;
            this.Leg4B.zRot += f16;
            this.Leg4C.zRot += f16;

            this.Leg5A.xRot = -10F / RAD;
            this.Leg5A.zRot = -75F / RAD;
            this.Leg5B.zRot = -60F / RAD;
            this.Leg5C.zRot = -75F / RAD;

            this.Leg5A.xRot -= f9;
            this.Leg5B.xRot = this.Leg5A.xRot;
            this.Leg5C.xRot = this.Leg5A.xRot;

            this.Leg5A.zRot -= f13;
            this.Leg5B.zRot -= f13;
            this.Leg5C.zRot -= f13;

            this.Leg6A.xRot = -30F / RAD;
            this.Leg6A.zRot = -70F / RAD;
            this.Leg6B.zRot = -60F / RAD;
            this.Leg6C.zRot = -70F / RAD;

            this.Leg6A.xRot -= f10;
            this.Leg6B.xRot = this.Leg6A.xRot;
            this.Leg6C.xRot = this.Leg6A.xRot;

            this.Leg6A.zRot -= f14;
            this.Leg6B.zRot -= f14;
            this.Leg6C.zRot -= f14;

            this.Leg7A.xRot = -45F / RAD;
            this.Leg7A.zRot = -70F / RAD;
            this.Leg7B.zRot = -60F / RAD;
            this.Leg7C.zRot = -70F / RAD;

            this.Leg7A.xRot -= f11;
            this.Leg7B.xRot = this.Leg7A.xRot;
            this.Leg7C.xRot = this.Leg7A.xRot;

            this.Leg7A.zRot -= f15;
            this.Leg7B.zRot -= f15;
            this.Leg7C.zRot -= f15;

            this.Leg8A.xRot = -60F / RAD;
            this.Leg8A.zRot = -70F / RAD;
            this.Leg8B.zRot = -60F / RAD;
            this.Leg8C.zRot = -70F / RAD;

            this.Leg8A.xRot -= f12;
            this.Leg8B.xRot = this.Leg8A.xRot;
            this.Leg8C.xRot = this.Leg8A.xRot;

            this.Leg8A.zRot -= f16;
            this.Leg8B.zRot -= f16;
            this.Leg8C.zRot -= f16;
        }

    
	}
}
