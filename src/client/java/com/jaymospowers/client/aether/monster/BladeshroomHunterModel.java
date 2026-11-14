/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/BladeshroomHunterModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, attack, burying and rustle posed in code from our own render state (those animations there are data files, not used).
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.monster;


import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

final class BladeshroomHunterModel extends AetherMonsterModel {


	private final ModelPart body;
	private final ModelPart shrink_group;
	private final ModelPart leg_front_left;
	private final ModelPart leg_front_left_joint;
	private final ModelPart leg_front_right;
	private final ModelPart leg_front_right_joint;
	private final ModelPart leg_back_right;
	private final ModelPart upper_back_leg_right;
	private final ModelPart leg_back_right_joint;
	private final ModelPart leg_back_left2;
	private final ModelPart upper_back_leg_left2;
	private final ModelPart leg_back_left_joint2;
	private final ModelPart neck;
	private final ModelPart head;
	private final ModelPart branch_left;
	private final ModelPart leaves_left;
	private final ModelPart branch_right;
	private final ModelPart leaves_right;



	private BladeshroomHunterModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.shrink_group = this.body.getChild("shrink_group");
		this.leg_front_left = this.shrink_group.getChild("leg_front_left");
		this.leg_front_left_joint = this.leg_front_left.getChild("leg_front_left_joint");
		this.leg_front_right = this.shrink_group.getChild("leg_front_right");
		this.leg_front_right_joint = this.leg_front_right.getChild("leg_front_right_joint");
		this.leg_back_right = this.shrink_group.getChild("leg_back_right");
		this.upper_back_leg_right = this.leg_back_right.getChild("upper_back_leg_right");
		this.leg_back_right_joint = this.leg_back_right.getChild("leg_back_right_joint");
		this.leg_back_left2 = this.shrink_group.getChild("leg_back_left2");
		this.upper_back_leg_left2 = this.leg_back_left2.getChild("upper_back_leg_left2");
		this.leg_back_left_joint2 = this.leg_back_left2.getChild("leg_back_left_joint2");
		this.neck = this.shrink_group.getChild("neck");
		this.head = this.neck.getChild("head");
		this.branch_left = this.body.getChild("branch_left");
		this.leaves_left = this.branch_left.getChild("leaves_left");
		this.branch_right = this.body.getChild("branch_right");
		this.leaves_right = this.branch_right.getChild("leaves_right");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 11.0F, 3.0F));

		PartDefinition shrink_group = body.addOrReplaceChild("shrink_group", CubeListBuilder.create().texOffs(39, 17).addBox(-2.0F, -3.5F, -6.0F, 4.0F, 6.0F, 9.0F, new CubeDeformation(0.0F))
				.texOffs(0, 30).addBox(-4.0F, -5.0F, -12.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
				.texOffs(34, 32).addBox(-4.0F, -5.0F, 1.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition chest_shroom_right_r1 = shrink_group.addOrReplaceChild("chest_shroom_right_r1", CubeListBuilder.create().texOffs(32, 3).addBox(0.0F, -6.0F, -3.0F, 0.0F, 11.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.25F, -2.25F, -11.75F, -0.3054F, 0.0F, -0.1745F));

		PartDefinition chest_shroom_left_r1 = shrink_group.addOrReplaceChild("chest_shroom_left_r1", CubeListBuilder.create().texOffs(32, -8).addBox(0.0F, -6.0F, -3.0F, 0.0F, 11.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.25F, -2.25F, -11.75F, -0.3054F, 0.0F, 0.1745F));

		PartDefinition leg_front_left = shrink_group.addOrReplaceChild("leg_front_left", CubeListBuilder.create(), PartPose.offset(3.5F, 0.0F, -7.5F));

		PartDefinition leg_front_left_1_r1 = leg_front_left.addOrReplaceChild("leg_front_left_1_r1", CubeListBuilder.create().texOffs(16, 46).addBox(-3.0F, -6.0F, 1.0F, 3.0F, 8.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, 4.0F, -2.5F, 0.1309F, 0.0F, -0.0873F));

		PartDefinition leg_front_left_joint = leg_front_left.addOrReplaceChild("leg_front_left_joint", CubeListBuilder.create().texOffs(20, 69).addBox(1.5F, 4.0F, -6.5F, 0.0F, 4.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(1.25F, 5.0F, 0.0F));

		PartDefinition leg_front_left_2_r1 = leg_front_left_joint.addOrReplaceChild("leg_front_left_2_r1", CubeListBuilder.create().texOffs(18, 59).addBox(-1.0F, -10.0F, -1.0F, 2.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, 8.0F, -4.0F, -0.3914F, -0.0334F, -0.0807F));

		PartDefinition foot_front_left_2_r1 = leg_front_left_joint.addOrReplaceChild("foot_front_left_2_r1", CubeListBuilder.create().texOffs(20, 65).addBox(0.0F, -2.0F, -3.5F, 0.0F, 4.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, 6.0F, -3.0F, 0.0F, -1.5708F, 0.0F));

		PartDefinition leg_front_right = shrink_group.addOrReplaceChild("leg_front_right", CubeListBuilder.create(), PartPose.offset(-3.5F, 0.0F, -7.5F));

		PartDefinition leg_front_right_1_r1 = leg_front_right.addOrReplaceChild("leg_front_right_1_r1", CubeListBuilder.create().texOffs(0, 46).mirror().addBox(0.0F, -6.0F, 1.0F, 3.0F, 8.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-2.5F, 4.0F, -2.5F, 0.1309F, 0.0F, 0.0873F));

		PartDefinition leg_front_right_joint = leg_front_right.addOrReplaceChild("leg_front_right_joint", CubeListBuilder.create().texOffs(0, 69).mirror().addBox(-1.5F, 4.0F, -6.5F, 0.0F, 4.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-1.25F, 5.0F, 0.0F));

		PartDefinition leg_front_right_2_r1 = leg_front_right_joint.addOrReplaceChild("leg_front_right_2_r1", CubeListBuilder.create().texOffs(2, 59).mirror().addBox(-1.0F, -10.0F, -1.0F, 2.0F, 8.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-1.5F, 8.0F, -4.0F, -0.3914F, 0.0334F, 0.0807F));

		PartDefinition foot_front_right_2_r1 = leg_front_right_joint.addOrReplaceChild("foot_front_right_2_r1", CubeListBuilder.create().texOffs(0, 65).mirror().addBox(0.0F, -2.0F, -3.5F, 0.0F, 4.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-1.5F, 6.0F, -3.0F, 0.0F, 1.5708F, 0.0F));

		PartDefinition leg_back_right = shrink_group.addOrReplaceChild("leg_back_right", CubeListBuilder.create(), PartPose.offset(-4.0F, 0.0F, 6.0F));

		PartDefinition upper_back_leg_right = leg_back_right.addOrReplaceChild("upper_back_leg_right", CubeListBuilder.create().texOffs(40, 48).mirror().addBox(-2.01F, -5.0F, -4.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false)
				.texOffs(72, 45).mirror().addBox(-3.0F, -12.0F, -3.0F, 5.0F, 8.0F, 6.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-1.0F, 4.0F, 3.0F, 0.2618F, 0.0F, 0.2182F));

		PartDefinition leg_back_right_joint = leg_back_right.addOrReplaceChild("leg_back_right_joint", CubeListBuilder.create().texOffs(40, 61).mirror().addBox(0.0F, -2.0F, -3.0F, 0.0F, 10.0F, 8.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(-2.0F, 5.0F, 3.0F));

		PartDefinition leg_back_right__r1 = leg_back_right_joint.addOrReplaceChild("leg_back_right__r1", CubeListBuilder.create().texOffs(40, 53).mirror().addBox(0.0F, -10.0F, -3.0F, 0.0F, 10.0F, 6.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 1.5708F, 0.0F));

		PartDefinition leg_back_left2 = shrink_group.addOrReplaceChild("leg_back_left2", CubeListBuilder.create(), PartPose.offset(4.0F, 0.0F, 6.0F));

		PartDefinition upper_back_leg_left2 = leg_back_left2.addOrReplaceChild("upper_back_leg_left2", CubeListBuilder.create().texOffs(56, 48).addBox(-1.99F, -5.0F, -4.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(94, 45).addBox(-2.0F, -12.0F, -3.0F, 5.0F, 8.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 4.0F, 3.0F, 0.2618F, 0.0F, -0.2182F));

		PartDefinition leg_back_left_joint2 = leg_back_left2.addOrReplaceChild("leg_back_left_joint2", CubeListBuilder.create().texOffs(56, 61).addBox(0.0F, -2.0F, -3.0F, 0.0F, 10.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(2.0F, 5.0F, 3.0F));

		PartDefinition leg_back_left_2_r1 = leg_back_left_joint2.addOrReplaceChild("leg_back_left_2_r1", CubeListBuilder.create().texOffs(56, 53).addBox(0.0F, -10.0F, -3.0F, 0.0F, 10.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

		PartDefinition neck = shrink_group.addOrReplaceChild("neck", CubeListBuilder.create(), PartPose.offset(0.0F, -4.5F, -10.0F));

		PartDefinition neck_r1 = neck.addOrReplaceChild("neck_r1", CubeListBuilder.create().texOffs(18, 16).addBox(-1.0F, -1.0F, -3.0F, 2.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.5F, -1.0F, -0.3054F, 0.0F, 0.0F));

		PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5F, -4.0F, -6.0F, 3.0F, 8.0F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.25F, -3.25F));

		PartDefinition blade_left_r1 = head.addOrReplaceChild("blade_left_r1", CubeListBuilder.create().texOffs(73, 1).addBox(-1.0F, -7.0F, -5.0F, 1.0F, 11.0F, 11.0F, new CubeDeformation(0.0F))
				.texOffs(0, -16).addBox(0.02F, -9.0F, -7.0F, 0.0F, 16.0F, 16.0F, new CubeDeformation(0.0F))
				.texOffs(0, -16).addBox(-1.02F, -9.0F, -7.0F, 0.0F, 16.0F, 16.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 0.25F, -2.75F, 0.7854F, 0.0F, 0.0F));

		PartDefinition branch_left = body.addOrReplaceChild("branch_left", CubeListBuilder.create().texOffs(22, 80).addBox(0.4226F, -13.684F, -0.2993F, 11.0F, 15.0F, 0.0F, new CubeDeformation(0.0F))
				.texOffs(22, 87).addBox(0.4226F, -16.684F, -7.2993F, 0.0F, 18.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, -5.0F, 9.0F, -0.6109F, 0.0F, 0.4363F));

		PartDefinition leaves_left = branch_left.addOrReplaceChild("leaves_left", CubeListBuilder.create(), PartPose.offset(5.4226F, -9.684F, -0.2993F));

		PartDefinition leaves_left_r1 = leaves_left.addOrReplaceChild("leaves_left_r1", CubeListBuilder.create().texOffs(22, 113).addBox(-3.0F, 0.0F, 0.0F, 7.0F, 14.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.6109F, 0.0F, 0.0F));

		PartDefinition branch_right = body.addOrReplaceChild("branch_right", CubeListBuilder.create().texOffs(0, 80).mirror().addBox(-11.4226F, -13.684F, -0.2993F, 11.0F, 15.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false)
				.texOffs(0, 87).mirror().addBox(-0.4226F, -16.684F, -7.2993F, 0.0F, 18.0F, 8.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-2.0F, -5.0F, 9.0F, -0.6109F, 0.0F, -0.4363F));

		PartDefinition leaves_right = branch_right.addOrReplaceChild("leaves_right", CubeListBuilder.create(), PartPose.offset(-5.4226F, -9.684F, -0.2993F));

		PartDefinition leaves_right_r1 = leaves_right.addOrReplaceChild("leaves_right_r1", CubeListBuilder.create().texOffs(0, 113).mirror().addBox(-4.0F, 0.0F, 0.0F, 7.0F, 14.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.6109F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	static BladeshroomHunterModel create() {
		return new BladeshroomHunterModel(createBodyLayer().bakeRoot());
	}


	/** Buried, the body drops this far (pixels) so only the branches stand out of the ground. */
	private static final float BURY_DROP = 17.0F;
	/** The cut: it rears back until this tick, slashes over the next few, then recovers. */
	private static final float WINDUP = 18.0F, SLASH = 3.0F, RECOVER = 14.0F, REAR = 0.7F, CUT = 1.1F;

	@Override
	public void setupAnim(AetherMonsterRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		this.head.xRot = (state.xRot * Mth.DEG_TO_RAD) + 0.0873F;
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;

		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		this.leg_back_right.xRot = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.6F * speed;
		this.leg_back_left2.xRot = Mth.cos(state.walkAnimationPos * 0.6662F + (float) Math.PI) * 0.6F * speed;
		this.leg_front_right.xRot = Mth.cos(state.walkAnimationPos * 0.6662F + (float) Math.PI) * 0.6F * speed;
		this.leg_front_left.xRot = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.6F * speed;

		// the branches rustle
		this.branch_left.zRot += Mth.sin(t * 0.11F) * 0.04F;
		this.branch_right.zRot -= Mth.sin(t * 0.13F + 1.0F) * 0.04F;
		// buried (progress 1): the body shrinks away under the branches, which settle on the ground
		float buried = smooth(state.progress);
		float shrink = 1.0F - buried;
		this.shrink_group.xScale = shrink;
		this.shrink_group.yScale = shrink;
		this.shrink_group.zScale = shrink;
		this.shrink_group.visible = shrink > 0.02F;
		this.body.y += buried * BURY_DROP;
		// the cut: rear back, then slash the blade down
		if (state.strike >= 0.0F) {
			float s = state.strike;
			float windup = s < WINDUP ? smooth(s / WINDUP) : 1.0F - smooth((s - WINDUP) / (SLASH * 2.0F));
			float slash = s >= WINDUP ? smooth((s - WINDUP) / SLASH) * (1.0F - smooth((s - WINDUP - SLASH * 2.0F) / RECOVER)) : 0.0F;
			this.neck.xRot += -REAR * windup + CUT * slash;
			this.head.xRot += -REAR * 0.4F * windup + CUT * 0.5F * slash;
		}
	}
}
