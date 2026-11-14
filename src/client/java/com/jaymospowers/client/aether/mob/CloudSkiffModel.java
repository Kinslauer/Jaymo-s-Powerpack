/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/CloudSkiffModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, posed from our own render state (the fold animation is not played: ours is picked up at once).
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.mob;

import com.jaymospowers.client.aether.anim.CloudSkiffAnimations;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

final class CloudSkiffModel extends AetherAnimalModel {
	private final KeyframeAnimation unfoldAnimation;
	private final KeyframeAnimation foldAnimation;
	private final ModelPart cloudSkiff;
	private final ModelPart wingLeft;
	private final ModelPart wingRight;
	private final ModelPart sailRudder;
	private final ModelPart handle;

	private CloudSkiffModel(ModelPart root) {
		super(root);
		this.unfoldAnimation = CloudSkiffAnimations.UNFOLD.bake(root);
		this.foldAnimation = CloudSkiffAnimations.FOLD.bake(root);
		this.cloudSkiff = root.getChild("cloud_skiff");
		this.wingLeft = this.cloudSkiff.getChild("wing_left");
		this.wingRight = this.cloudSkiff.getChild("wing_right");
		this.sailRudder = this.cloudSkiff.getChild("sail_rudder");
		this.handle = this.sailRudder.getChild("handle");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition skiff = partdefinition.addOrReplaceChild("cloud_skiff", CubeListBuilder.create().texOffs(28, 55).addBox(-9.0F, -3.0F, -16.0F, 18.0F, 2.0F, 32.0F, new CubeDeformation(0.0F))
		.texOffs(62, 28).addBox(-4.0F, -1.0F, -12.0F, 8.0F, 1.0F, 25.0F, new CubeDeformation(0.0F))
		.texOffs(52, 64).addBox(0.0F, 0.0F, -13.0F, 0.0F, 4.0F, 26.0F, new CubeDeformation(0.0F))
		.texOffs(65, 42).addBox(-2.0F, -4.5F, 13.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 2.0F));

		skiff.addOrReplaceChild("wing_left", CubeListBuilder.create().texOffs(73, 115).addBox(-1.0F, 0.0F, -9.0F, 5.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(73, 99).addBox(-1.0F, 0.0F, 7.0F, 5.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(60, 95).addBox(4.0F, -1.0F, -14.0F, 6.0F, 2.0F, 28.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(8.5F, -2.0F, 0.0F, 0.0F, 0.0F, 0.2618F));

		skiff.addOrReplaceChild("wing_right", CubeListBuilder.create().texOffs(41, 118).addBox(-4.0F, 0.0F, -9.0F, 5.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(41, 102).addBox(-4.0F, 0.0F, 7.0F, 5.0F, 1.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 98).addBox(-10.0F, -1.0F, -14.0F, 6.0F, 2.0F, 28.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-8.5F, -2.0F, 0.0F, 0.0F, 0.0F, -0.2618F));

		PartDefinition rudder = skiff.addOrReplaceChild("sail_rudder", CubeListBuilder.create().texOffs(69, 8).addBox(-1.0F, -30.5F, -1.0F, 2.0F, 31.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(65, 1).addBox(-2.0F, -32.5F, -2.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(81, 12).addBox(-2.0F, -12.5F, -2.0F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, -32).addBox(0.0F, -30.5F, -1.0F, 0.0F, 48.0F, 32.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -5.0F, 15.0F));

		rudder.addOrReplaceChild("handle", CubeListBuilder.create().texOffs(78, 0).addBox(-1.0F, -1.0F, -9.0F, 2.0F, 2.0F, 9.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -10.5F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	static CloudSkiffModel create() {
		return new CloudSkiffModel(createBodyLayer().bakeRoot());
	}


	/** It unfolds over its first ticks in the world. */
	private static final float UNFOLD_TICKS = CloudSkiffAnimations.UNFOLD.lengthInSeconds() * 20.0F;
	/** How far the wings lift level once it is off the ground (they droop this much at rest). */
	private static final float WING_LIFT = 0.2618F;

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		if (state.ageInTicks < UNFOLD_TICKS) {
			this.unfoldAnimation.apply((long)(state.ageInTicks * 50.0F), 1.0F);
		}
		// the rudder leans into a turn and idles a little in the wind
		this.sailRudder.yRot += state.steer + Mth.sin(3.0F * state.ageInTicks * Mth.DEG_TO_RAD) / 15.0F;
		this.wingLeft.zRot -= WING_LIFT * state.air;
		this.wingRight.zRot += WING_LIFT * state.air;
	}
}
