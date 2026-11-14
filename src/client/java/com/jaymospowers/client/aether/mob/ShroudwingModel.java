/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/ShroudwingModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, posed from our own render state.
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.mob;

import com.jaymospowers.client.aether.anim.ShroudwingAnimations;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.util.Mth;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

final class ShroudwingModel extends AetherAnimalModel {
	private final KeyframeAnimation flyingAnimation;
	private final KeyframeAnimation landAnimation;
	private final KeyframeAnimation takeoffAnimation;
	private final KeyframeAnimation walkAnimation;
	private final ModelPart shroudwing;
	private final ModelPart rightWing;
	private final ModelPart leftWing;

	private ShroudwingModel(ModelPart root) {
		super(root);
		this.flyingAnimation = ShroudwingAnimations.FLYING.bake(root);
		this.landAnimation = ShroudwingAnimations.LAND.bake(root);
		this.takeoffAnimation = ShroudwingAnimations.TAKEOFF.bake(root);
		this.walkAnimation = ShroudwingAnimations.WALK.bake(root);
		this.shroudwing = root.getChild("shroudwing");
		this.rightWing = this.shroudwing.getChild("right_wing");
		this.leftWing = this.shroudwing.getChild("left_wing");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition partDefinition = meshDefinition.getRoot();

		PartDefinition shroudwing = partDefinition.addOrReplaceChild("shroudwing", CubeListBuilder.create().texOffs(28, 0).addBox(-1.0F, -3.0F, -3.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(12, 23).addBox(-2.0F, -1.0F, -3.0F, 3.0F, 2.0F, 7.0F, new CubeDeformation(0.0F))
				.texOffs(-3, 0).addBox(-3.0F, 0.0F, -5.25F, 5.0F, 0.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.5F, 23.0F, 0.0F));

		PartDefinition leftWingBase = shroudwing.addOrReplaceChild("left_wing_base", CubeListBuilder.create(), PartPose.offset(0.5F, -1.1F, -1.0F));
		leftWingBase.addOrReplaceChild("left_wing_case_r1", CubeListBuilder.create().texOffs(0, 19).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.3054F, 0.1745F, 0.0F));

		PartDefinition rightWingbase = shroudwing.addOrReplaceChild("right_wing_base", CubeListBuilder.create(), PartPose.offset(-1.5F, -1.1F, -1.0F));
		rightWingbase.addOrReplaceChild("right_wing_case_r1", CubeListBuilder.create().texOffs(0, 26).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.3054F, -0.1745F, 0.0F));

		PartDefinition rightWing = shroudwing.addOrReplaceChild("right_wing", CubeListBuilder.create(), PartPose.offset(-1.25F, -1.1F, 0.25F));
		rightWing.addOrReplaceChild("right_wing_r1", CubeListBuilder.create().texOffs(1, 11).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.3054F, -0.1745F, 0.0F));

		PartDefinition leftWing = shroudwing.addOrReplaceChild("left_wing", CubeListBuilder.create(), PartPose.offset(0.25F, -1.1F, 0.25F));
		leftWing.addOrReplaceChild("left_wing_r1", CubeListBuilder.create().texOffs(-4, 11).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 0.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.3054F, 0.1745F, 0.0F));

		return LayerDefinition.create(meshDefinition, 32, 32);
	}

	static ShroudwingModel create() {
		return new ShroudwingModel(createBodyLayer().bakeRoot());
	}


	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		if (state.resting) {
			this.landAnimation.apply((long)(state.actionTime * 50.0F), 1.0F);
			this.walkAnimation.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 16.0F, 5.0F);
		} else {
			this.flyingAnimation.apply((long)(state.ageInTicks * 50.0F), 1.0F);
			float rotation = (Mth.sin(state.ageInTicks * 2) * 10 * Mth.DEG_TO_RAD);
			this.rightWing.zRot = rotation;
			this.leftWing.zRot = rotation;
		}
	}
}
