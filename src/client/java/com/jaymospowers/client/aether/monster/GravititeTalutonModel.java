/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/GravititeTalutonModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, spin and charge posed in code from our own render state (those animations there are data files, not used).
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.monster;

import com.jaymospowers.client.aether.anim.GravititeTalutonAnimation;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

final class GravititeTalutonModel extends AetherMonsterModel {
	private final KeyframeAnimation reloadAnimation;
	private final ModelPart body;
	private final ModelPart legsNode;
	private final ModelPart legLeftPosition;
	private final ModelPart legLeft;
	private final ModelPart legRightPosition;
	private final ModelPart legRight;
	private final ModelPart legRearPosition;
	private final ModelPart legRear;
	private final ModelPart debrisNode;
	private final ModelPart debris1;
	private final ModelPart debris2;

	private GravititeTalutonModel(ModelPart root) {
		super(root);
		this.reloadAnimation = GravititeTalutonAnimation.RELOAD.bake(root);
		this.body = root.getChild("body");
		this.legsNode = this.body.getChild("legs_node");
		this.legLeftPosition = this.legsNode.getChild("leg_left_position");
		this.legLeft = this.legLeftPosition.getChild("leg_left");
		this.legRightPosition = this.legsNode.getChild("leg_right_position");
		this.legRight = this.legRightPosition.getChild("leg_right");
		this.legRearPosition = this.legsNode.getChild("leg_rear_position");
		this.legRear = this.legRearPosition.getChild("leg_rear");
		this.debrisNode = this.body.getChild("debris_node");
		this.debris1 = this.debrisNode.getChild("debris_1");
		this.debris2 = this.debrisNode.getChild("debris_2");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();

		PartDefinition body = partDefinition.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 14.0F, 0.0F));

		PartDefinition rock_b_r1 = body.addOrReplaceChild("rock_b_r1", CubeListBuilder.create().texOffs(0, 3).addBox(-1.5F, -5.1F, -0.9F, 3.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.7F, 5.0F, 0.1745F, 0.0F, 0.0F));

		PartDefinition rock_b_l_r1 = body.addOrReplaceChild("rock_b_l_r1", CubeListBuilder.create().texOffs(71, 3).addBox(-0.5F, -4.0F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, -1.7F, 3.7F, 0.2182F, 0.7854F, 0.0F));

		PartDefinition rock_f_r_r1 = body.addOrReplaceChild("rock_f_r_r1", CubeListBuilder.create().texOffs(33, 3).addBox(-1.5F, -4.0F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5F, -2.7F, -2.1F, -0.2182F, 0.7854F, 0.0F));

		PartDefinition rock_b_r_r1 = body.addOrReplaceChild("rock_b_r_r1", CubeListBuilder.create().texOffs(11, 5).addBox(-1.5F, -2.0F, -1.0F, 3.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, -2.7F, 2.8F, 0.4363F, -0.7854F, 0.0F));

		PartDefinition rock_f_l_r1 = body.addOrReplaceChild("rock_f_l_r1", CubeListBuilder.create().texOffs(53, 8).addBox(-0.5F, -1.0F, 0.0F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.8F, -2.4F, -3.5F, -0.1745F, -0.7854F, 0.0F));

		PartDefinition rock_f_r1 = body.addOrReplaceChild("rock_f_r1", CubeListBuilder.create().texOffs(42, 4).addBox(-1.5F, -4.0F, -1.0F, 3.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.7F, -5.0F, -0.2618F, 0.0F, 0.0F));

		PartDefinition rock_r_r1 = body.addOrReplaceChild("rock_r_r1", CubeListBuilder.create().texOffs(22, 3).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 6.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.8F, -5.0F, -0.5F, 0.0F, 0.0F, 0.1745F));

		PartDefinition rock_l_r1 = body.addOrReplaceChild("rock_l_r1", CubeListBuilder.create().texOffs(60, 0).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 9.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.4F, -4.7F, -0.5F, 0.0F, 0.0F, -0.1745F));

		PartDefinition body_3_r1 = body.addOrReplaceChild("body_3_r1", CubeListBuilder.create().texOffs(96, 0).addBox(-2.5F, -6.0F, -2.5F, 5.0F, 9.0F, 5.0F, new CubeDeformation(0.0F))
				.texOffs(92, 14).addBox(-3.5F, 3.0F, -3.5F, 7.0F, 6.0F, 7.0F, new CubeDeformation(0.0F))
				.texOffs(84, 27).addBox(-5.5F, 9.0F, -5.5F, 11.0F, 4.0F, 11.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -9.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		PartDefinition legs_node = body.addOrReplaceChild("legs_node", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition leg_left_position = legs_node.addOrReplaceChild("leg_left_position", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		PartDefinition leg_left = leg_left_position.addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(68, 21).addBox(-1.5F, -4.2F, -3.8F, 3.0F, 11.0F, 2.0F, new CubeDeformation(0.0F))
				.texOffs(65, 13).addBox(-2.5F, -2.2F, -2.8F, 5.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -6.0F, 0.5236F, 0.0F, 0.0F));

		PartDefinition leg_right_position = legs_node.addOrReplaceChild("leg_right_position", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 0.7854F, 0.0F));

		PartDefinition leg_right = leg_right_position.addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(15, 21).addBox(-1.5F, -4.2F, -3.8F, 3.0F, 11.0F, 2.0F, new CubeDeformation(0.0F))
				.texOffs(12, 13).addBox(-2.5F, -2.2F, -2.8F, 5.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -6.0F, 0.5236F, 0.0F, 0.0F));

		PartDefinition leg_rear_position = legs_node.addOrReplaceChild("leg_rear_position", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, 0.0F, 3.1416F, 0.0F));

		PartDefinition leg_rear = leg_rear_position.addOrReplaceChild("leg_rear", CubeListBuilder.create().texOffs(42, 21).addBox(-1.5F, -4.2F, -3.8F, 3.0F, 11.0F, 2.0F, new CubeDeformation(0.0F))
				.texOffs(39, 13).addBox(-2.5F, -2.2F, -2.8F, 5.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -1.0F, -6.0F, 0.5236F, 0.0F, 0.0F));

		PartDefinition debris_node = body.addOrReplaceChild("debris_node", CubeListBuilder.create(), PartPose.offset(0.0F, -3.0F, 0.0F));

		PartDefinition debris_1 = debris_node.addOrReplaceChild("debris_1", CubeListBuilder.create().texOffs(0, 36).addBox(-14.0F, -9.0F, -14.0F, 28.0F, 18.0F, 28.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition debris_2 = debris_node.addOrReplaceChild("debris_2", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition debris_2_r1 = debris_2.addOrReplaceChild("debris_2_r1", CubeListBuilder.create().texOffs(0, 82).addBox(-14.0F, -9.0F, -14.0F, 28.0F, 18.0F, 28.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		return LayerDefinition.create(meshDefinition, 128, 128);
	}

	static GravititeTalutonModel create() {
		return new GravititeTalutonModel(createBodyLayer().bakeRoot());
	}


	/** How fast the core turns and the debris circles (radians a tick), at rest and charging; the legs' stride. */
	private static final float SPIN = 0.05F, SPIN_CHARGING = 0.22F, DEBRIS = 0.03F, DEBRIS_CHARGING = 0.3F, STRIDE = 0.3F;
	/** Charging, the debris ring pulses by this much. */
	private static final float PULSE = 0.12F;

	@Override
	public void setupAnim(AetherMonsterRenderer.State state) {
		super.setupAnim(state);
		float t = state.ageInTicks;
		// the core turns on its own while the legs stay put; the debris circles the other way, fast when it charges
		float spin = t * (state.glowing ? SPIN_CHARGING : SPIN);
		this.body.yRot = spin;
		this.legsNode.yRot = -spin;
		float ring = -spin - t * (state.glowing ? DEBRIS_CHARGING : DEBRIS);
		this.debris1.yRot = ring;
		this.debris2.yRot = ring;
		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		this.legLeft.xRot += Mth.cos(state.walkAnimationPos * 0.6662F) * STRIDE * speed;
		this.legRight.xRot += Mth.cos(state.walkAnimationPos * 0.6662F + Mth.HALF_PI) * STRIDE * speed;
		this.legRear.xRot += Mth.cos(state.walkAnimationPos * 0.6662F + Mth.PI) * STRIDE * speed;
		if (state.glowing) {
			float pulse = 1.0F + Mth.sin(t * 1.0F) * PULSE;
			this.debrisNode.xScale = pulse;
			this.debrisNode.zScale = pulse;
		} else {
			// the volley thrown (0): new debris rises into place
			this.reloadAnimation.apply((long)(state.actionTime * 50.0F), 2.0F);
		}
	}
}
