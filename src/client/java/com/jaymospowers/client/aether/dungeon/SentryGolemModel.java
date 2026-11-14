/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/SentryGolemModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, posed from our own render state.
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.dungeon;

import com.jaymospowers.client.aether.anim.SentryGolemAnimation;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

final class SentryGolemModel extends DungeonModel {
	private final KeyframeAnimation runAnimation;
	private final KeyframeAnimation walkAnimation;
	private final KeyframeAnimation idleAnimation;
	private final KeyframeAnimation checkSelfAnimation;
	private final KeyframeAnimation lookAroundAnimation;
	private final KeyframeAnimation rangeAttackAnimation;
	private final KeyframeAnimation attackAnimation;
	private final KeyframeAnimation rangeAttackReadyAnimation;
	private final KeyframeAnimation attackReadyAnimation;


	private final ModelPart body;
	private final ModelPart torso;
	private final ModelPart head;
	private final ModelPart left_shoulder;
	private final ModelPart right_shoulder;
	private final ModelPart left_arm;
	private final ModelPart left_arm_bottom;
	private final ModelPart right_arm;
	private final ModelPart right_arm_bottom;
	private final ModelPart left_leg;
	private final ModelPart right_leg;

	private SentryGolemModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.torso = this.body.getChild("torso");
		this.head = this.torso.getChild("head");
		this.left_shoulder = this.torso.getChild("left_shoulder");
		this.right_shoulder = this.torso.getChild("right_shoulder");
		this.left_arm = this.torso.getChild("left_arm");
		this.left_arm_bottom = this.left_arm.getChild("left_arm_bottom");
		this.right_arm = this.torso.getChild("right_arm");
		this.right_arm_bottom = this.right_arm.getChild("right_arm_bottom");
		this.left_leg = this.body.getChild("left_leg");
		this.right_leg = this.body.getChild("right_leg");
		this.runAnimation = SentryGolemAnimation.run.bake(root);
		this.walkAnimation = SentryGolemAnimation.walk.bake(root);
		this.idleAnimation = SentryGolemAnimation.idle.bake(root);
		this.checkSelfAnimation = SentryGolemAnimation.check_self.bake(root);
		this.lookAroundAnimation = SentryGolemAnimation.look_around.bake(root);
		this.rangeAttackAnimation = SentryGolemAnimation.fire_ranged_weapon.bake(root);
		this.attackAnimation = SentryGolemAnimation.swing_melee_weapon.bake(root);
		this.rangeAttackReadyAnimation = SentryGolemAnimation.ready_ranged_weapon.bake(root);
		this.attackReadyAnimation = SentryGolemAnimation.ready_melee_weapon.bake(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(-7.0F, -0.5F, 0.0F));

		PartDefinition torso = body.addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 23).addBox(-7.5F, -4.5F, -5.0F, 15.0F, 8.0F, 10.0F, new CubeDeformation(0.0F))
				.texOffs(0, 17).addBox(-5.5F, -6.5F, -5.0F, 11.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(0, 12).addBox(-2.0F, -5.5F, -1.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(24, 44).addBox(-2.0F, 3.5F, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(24, 44).addBox(-2.0F, 5.0F, -2.0F, 4.0F, 1.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(0, 51).addBox(-5.0F, 6.0F, -4.0F, 10.0F, 4.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(7.0F, 2.5F, 0.0F));

		PartDefinition rotator = torso.addOrReplaceChild("rotator", CubeListBuilder.create().texOffs(0, 41).addBox(-4.0F, -1.0F, -4.0F, 8.0F, 2.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 5.0F, 0.0F));

		PartDefinition head = torso.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -3.0F, -4.0F, 7.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -6.0F, 0.0F));

		PartDefinition left_shoulder = torso.addOrReplaceChild("left_shoulder", CubeListBuilder.create(), PartPose.offset(7.0F, -2.5F, 0.0F));

		PartDefinition left_shoulder_guard_r1 = left_shoulder.addOrReplaceChild("left_shoulder_guard_r1", CubeListBuilder.create().texOffs(50, 0).mirror().addBox(0.0F, -4.0F, -4.0F, 8.0F, 6.0F, 8.0F, new CubeDeformation(0.0F)).mirror(false)
				.texOffs(50, 14).mirror().addBox(0.5F, -3.0F, -3.0F, 7.0F, 5.0F, 6.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.1309F, 0.2182F));

		PartDefinition right_shoulder = torso.addOrReplaceChild("right_shoulder", CubeListBuilder.create(), PartPose.offset(-7.0F, -2.5F, 0.0F));

		PartDefinition right_shoulder_r1 = right_shoulder.addOrReplaceChild("right_shoulder_r1", CubeListBuilder.create().texOffs(88, 14).addBox(-7.5F, -3.0F, -3.0F, 7.0F, 5.0F, 6.0F, new CubeDeformation(0.0F))
				.texOffs(88, 0).addBox(-8.0F, -4.0F, -4.0F, 8.0F, 6.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.1309F, -0.2182F));

		PartDefinition left_arm = torso.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(50, 25).mirror().addBox(-2.0F, -1.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false)
				.texOffs(66, 25).mirror().addBox(-1.0F, 4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(10.0F, -1.5F, 0.5F, 0.0873F, 0.0F, -0.1309F));

		PartDefinition left_arm_bottom = left_arm.addOrReplaceChild("left_arm_bottom", CubeListBuilder.create().texOffs(50, 34).mirror().addBox(-1.5F, -1.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false)
				.texOffs(76, 36).addBox(0.5F, 6.0F, -2.0F, 0.0F, 4.0F, 5.0F, new CubeDeformation(0.0F))
				.texOffs(66, 30).mirror().addBox(2.5F, -4.0F, -2.0F, 0.0F, 10.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition right_arm = torso.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(88, 25).addBox(-2.0F, -1.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(104, 25).addBox(-1.0F, 4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-10.0F, -1.5F, 0.5F, 0.0873F, 0.0F, 0.1309F));

		PartDefinition right_arm_bottom = right_arm.addOrReplaceChild("right_arm_bottom", CubeListBuilder.create().texOffs(88, 34).addBox(-2.5F, -1.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(104, 30).addBox(-2.5F, -4.0F, -2.0F, 0.0F, 10.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition hand = right_arm_bottom.addOrReplaceChild("hand", CubeListBuilder.create().texOffs(114, 31).addBox(-1.25F, -1.25F, -1.75F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(114, 37).addBox(-0.25F, -1.25F, -2.75F, 1.0F, 3.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(-0.75F, 7.25F, 0.25F));

		PartDefinition left_leg = body.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(50, 45).mirror().addBox(-1.5F, -2.5F, -2.5F, 5.0F, 9.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false)
				.texOffs(70, 46).mirror().addBox(-0.5F, 3.5F, -0.5F, 3.0F, 8.0F, 5.0F, new CubeDeformation(0.0F)).mirror(false)
				.texOffs(50, 48).mirror().addBox(1.0F, 8.0F, -4.0F, 0.0F, 4.0F, 11.0F, new CubeDeformation(0.0F)).mirror(false)
				.texOffs(72, 59).mirror().addBox(-3.0F, 8.0F, 2.0F, 8.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(11.5F, 12.5F, -1.0F));

		PartDefinition right_leg = body.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(88, 45).addBox(-3.5F, -2.5F, -2.5F, 5.0F, 9.0F, 5.0F, new CubeDeformation(0.0F))
				.texOffs(108, 46).addBox(-2.5F, 3.5F, -0.5F, 3.0F, 8.0F, 5.0F, new CubeDeformation(0.0F))
				.texOffs(88, 48).addBox(-1.0F, 8.0F, -4.0F, 0.0F, 4.0F, 11.0F, new CubeDeformation(0.0F))
				.texOffs(110, 59).addBox(-5.0F, 8.0F, 2.0F, 8.0F, 4.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(2.5F, 12.5F, -1.0F));

		return LayerDefinition.create(meshdefinition, 128, 64);
	}

	static SentryGolemModel create() {
		return new SentryGolemModel(createBodyLayer().bakeRoot());
	}


	/** The lengths (ms) of the animations that are scrubbed, and how long the follow-through of a throw plays (ticks). */
	private static final float READY_MS = 750.0F, SWING_MS = 667.0F, FIRE_TICKS = 30.0F;

	@Override
	public void setupAnim(DungeonRenderer.State state) {
		super.setupAnim(state);
		this.head.xRot = (state.xRot * Mth.DEG_TO_RAD) + 0.0873F;
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
		if (state.still) {
			// (a warden that has not woken stands as carved)
			return;
		}
		this.walkAnimation.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 1.0F, 1.0F);
		this.idleAnimation.applyWalk(state.ageInTicks, 1.0F, 1.0F, 1.0F);
		if (state.fidget == 1) {
			this.lookAroundAnimation.apply((long)(state.fidgetTime * 50.0F), 1.0F);
		} else if (state.fidget == 2) {
			this.checkSelfAnimation.apply((long)(state.fidgetTime * 50.0F), 1.0F);
		}
		if (state.progress > 0.0F) {
			// winding up a throw
			this.rangeAttackReadyAnimation.apply((long)(state.progress * READY_MS), 1.0F);
		} else if (state.actionTime < FIRE_TICKS) {
			// the throw itself, from when the windup ended
			this.rangeAttackAnimation.apply((long)(state.actionTime * 50.0F), 1.0F);
		}
		if (state.attack > 0.0F) {
			this.attackAnimation.apply((long)(state.attack * SWING_MS), 1.0F);
		}
	}
}
