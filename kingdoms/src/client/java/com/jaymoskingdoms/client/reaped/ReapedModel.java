package com.jaymoskingdoms.client.reaped;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.illager.IllagerModel;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.illager.AbstractIllager;

/**
 * A reaped illager's body (Andy: "keep the vindicator and pillager/evoker bodies with hanging arms and undead skulls
 * matching theirs"): an illager's frame gone to bone, our own model. An illager's skull (the tall head, the heavy brow
 * as a ridge of bone, a hole where the great nose was, a jaw hung under it), a ribcage inside what's left of its coat,
 * thin bone arms hanging out of torn sleeves, bone shins under ragged trousers into its boots, and for the evoker its
 * robe. It's posed as vanilla's illager is ({@link IllagerModel}: the same parts by name), so it walks, swings, aims and
 * casts the same way; its arms never cross. kingdoms/tools/gen_reaped.py paints it and holds the same boxes: keep the
 * two in step.
 */
final class ReapedModel extends IllagerModel<IllagerRenderState> {
	/** How far its head lolls to one side, how far its idle arms hang forward and how much they sway (radians). */
	static final float LOLL = 0.12F, HANG = 0.14F, SWAY = 0.05F;

	private final ModelPart head, rightArm, leftArm;

	ReapedModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
	}

	static LayerDefinition createBodyLayer(boolean robed) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 8.0F, 8.0F)
			.texOffs(0, 16).addBox(-3.0F, -2.0F, -3.5F, 6.0F, 2.0F, 6.0F)
			.texOffs(32, 0).addBox(-4.0F, -7.0F, -5.0F, 8.0F, 1.0F, 1.0F), PartPose.ZERO);
		// (vanilla's model asks for both by name; nothing wears the hat)
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("nose", CubeListBuilder.create().texOffs(32, 4).addBox(-1.0F, -5.0F, -5.0F, 2.0F, 2.0F, 1.0F), PartPose.ZERO);
		CubeListBuilder body = CubeListBuilder.create()
			.texOffs(0, 24).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 12.0F, 6.0F, new CubeDeformation(0.25F))
			.texOffs(28, 24).addBox(-3.0F, 0.0F, -2.0F, 6.0F, 11.0F, 4.0F);
		if (robed) {
			body.texOffs(64, 0).addBox(-4.0F, 0.0F, -3.0F, 8.0F, 18.0F, 6.0F, new CubeDeformation(0.5F));
		}
		root.addOrReplaceChild("body", body, PartPose.ZERO);
		// (the crossed arms: never shown, a reaped's arms hang)
		root.addOrReplaceChild("arms", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 3.0F, -1.0F, -0.75F, 0.0F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create()
			.texOffs(0, 42).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F)
			.texOffs(36, 38).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F)
			.texOffs(44, 38).addBox(-2.0F, 9.0F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offset(-2.0F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().mirror()
			.texOffs(0, 42).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F)
			.texOffs(36, 38).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 12.0F, 2.0F)
			.texOffs(44, 38).addBox(-2.0F, 9.0F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offset(2.0F, 12.0F, 0.0F));
		root.addOrReplaceChild("right_arm", CubeListBuilder.create()
			.texOffs(48, 24).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 7.0F, 4.0F)
			.texOffs(28, 38).addBox(-2.0F, -2.0F, -1.0F, 2.0F, 12.0F, 2.0F), PartPose.offset(-5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().mirror()
			.texOffs(48, 24).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 7.0F, 4.0F)
			.texOffs(28, 38).addBox(0.0F, -2.0F, -1.0F, 2.0F, 12.0F, 2.0F), PartPose.offset(5.0F, 2.0F, 0.0F));
		return LayerDefinition.create(mesh, 96, 64);
	}

	@Override
	public void setupAnim(IllagerRenderState state) {
		super.setupAnim(state);
		// the head hangs to one side; with nothing to do, the arms hang a little forward and sway
		this.head.zRot = LOLL;
		if (state.armPose == AbstractIllager.IllagerArmPose.NEUTRAL && !state.isRiding) {
			float sway = Mth.sin(state.ageInTicks * 0.06F) * SWAY;
			this.rightArm.xRot += -HANG + sway;
			this.leftArm.xRot += -HANG - sway;
			this.rightArm.zRot = 0.05F;
			this.leftArm.zRot = -0.05F;
		}
	}
}
