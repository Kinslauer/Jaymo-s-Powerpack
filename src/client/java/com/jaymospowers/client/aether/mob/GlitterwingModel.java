/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/GlitterwingModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, posed from our own render state.
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.mob;

import com.jaymospowers.client.aether.anim.GlitterwingAnimations;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

final class GlitterwingModel extends AetherAnimalModel {
	private final KeyframeAnimation flyingAnimation;
	private final KeyframeAnimation landAnimation;
	private final KeyframeAnimation takeOffAnimation;

	private final ModelPart glitterwing;
	private final ModelPart rightWing;
	private final ModelPart leftWing;

	private GlitterwingModel(ModelPart root) {
		super(root);
		this.flyingAnimation = GlitterwingAnimations.FLYING.bake(root);
		this.landAnimation = GlitterwingAnimations.LAND.bake(root);
		this.takeOffAnimation = GlitterwingAnimations.TAKEOFF.bake(root);
		this.glitterwing = root.getChild("glitterwing");
		this.rightWing = this.glitterwing.getChild("right_wing");
		this.leftWing = this.glitterwing.getChild("left_wing");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition glitterwing = partdefinition.addOrReplaceChild("glitterwing", CubeListBuilder.create().texOffs(-26, 26).addBox(-4.0F, -2.0F, -17.0F, 8.0F, 0.0F, 26.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 26.0F, 4.0F));
		glitterwing.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(-26, 0).addBox(-13.0F, 0.0F, -17.0F, 13.0F, 0.0F, 26.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.0F, 0.0F));
		glitterwing.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, 0.0F, -17.0F, 13.0F, 0.0F, 26.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -2.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	static GlitterwingModel create() {
		return new GlitterwingModel(createBodyLayer().bakeRoot());
	}


	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		this.flyingAnimation.applyWalk(state.ageInTicks, state.resting ? 0.0F : 1.0F, 1.0F, 1.0F);
		if (state.resting) {
			this.landAnimation.apply((long)(state.actionTime * 50.0F), 1.0F);
		}
	}
}
