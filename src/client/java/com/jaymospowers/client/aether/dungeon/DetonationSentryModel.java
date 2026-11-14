/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/DetonationSentryModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, posed from our own render state.
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.dungeon;

import com.jaymospowers.client.aether.anim.DetonationSentryAnimations;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

final class DetonationSentryModel extends DungeonModel {
	private final KeyframeAnimation walkAnimation;
	private final KeyframeAnimation explosionAnimation;
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart legs_pair_1;
	private final ModelPart leg_back_left;
	private final ModelPart leg_front_right;
	private final ModelPart legs_pair_2;
	private final ModelPart leg_back_right;
	private final ModelPart leg_front_left;

	private DetonationSentryModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = this.body.getChild("head");
		this.legs_pair_1 = this.body.getChild("legs_pair_1");
		this.leg_back_left = this.legs_pair_1.getChild("leg_back_left");
		this.leg_front_right = this.legs_pair_1.getChild("leg_front_right");
		this.legs_pair_2 = this.body.getChild("legs_pair_2");
		this.leg_back_right = this.legs_pair_2.getChild("leg_back_right");
		this.leg_front_left = this.legs_pair_2.getChild("leg_front_left");
		this.walkAnimation = DetonationSentryAnimations.walk.bake(root);
		this.explosionAnimation = DetonationSentryAnimations.explode.bake(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body = partdefinition.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 23.0F, 0.0F));

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 14.0F, new CubeDeformation(0.0F))
				.texOffs(57, 0).addBox(-7.0F, -7.0F, -7.0F, 14.0F, 14.0F, 14.0F, new CubeDeformation(0.3F)), PartPose.offset(0.0F, -7.0F, 0.0F));

		PartDefinition legs_pair_1 = body.addOrReplaceChild("legs_pair_1", CubeListBuilder.create(), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition leg_back_left = legs_pair_1.addOrReplaceChild("leg_back_left", CubeListBuilder.create().texOffs(40, 43).addBox(-2.0F, -1.5F, -1.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(57, 43).addBox(-2.0F, -1.5F, -1.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(7.0F, 0.0F, 7.0F, 0.2618F, 0.7854F, 0.0F));

		PartDefinition leg_front_right = legs_pair_1.addOrReplaceChild("leg_front_right", CubeListBuilder.create().texOffs(0, 30).addBox(-2.0F, -1.5F, -3.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(17, 30).addBox(-2.0F, -1.5F, -3.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(-7.0F, 0.0F, -7.0F, -0.2618F, 0.7854F, 0.0F));

		PartDefinition legs_pair_2 = body.addOrReplaceChild("legs_pair_2", CubeListBuilder.create(), PartPose.offset(0.0F, -4.0F, 0.0F));

		PartDefinition leg_back_right = legs_pair_2.addOrReplaceChild("leg_back_right", CubeListBuilder.create().texOffs(0, 43).addBox(-2.0F, -1.5F, -1.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(17, 43).addBox(-2.0F, -1.5F, -1.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(-7.0F, 0.0F, 7.0F, 0.2618F, -0.7854F, 0.0F));

		PartDefinition leg_front_left = legs_pair_2.addOrReplaceChild("leg_front_left", CubeListBuilder.create().texOffs(40, 30).addBox(-2.0F, -1.5F, -3.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F))
				.texOffs(57, 30).addBox(-2.0F, -1.5F, -3.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(7.0F, 0.0F, -7.0F, -0.2618F, -0.7854F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 64);
	}

	static DetonationSentryModel create() {
		return new DetonationSentryModel(createBodyLayer().bakeRoot());
	}


	/** The swelling before it bursts is scrubbed over the fuse (ms of the animation). */
	private static final float EXPLODE_MS = 583.0F;

	@Override
	public void setupAnim(DungeonRenderer.State state) {
		super.setupAnim(state);
		this.walkAnimation.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 1.0F, 1.0F);
		// a lit fuse makes it tremble, harder as it burns, and it swells to bursting
		if (state.progress > 0.0F) {
			this.head.zRot += Mth.sin(state.ageInTicks * 2.2F) * 0.03F * (0.5F + state.progress);
			this.explosionAnimation.apply((long)(state.progress * state.progress * EXPLODE_MS), 1.0F);
		}
	}
}
