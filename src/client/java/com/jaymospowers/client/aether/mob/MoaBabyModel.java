/*
 * From The Aether II by The Aether Team (https://github.com/The-Aether-Team/The-Aether-II), file
 * src/main/java/com/aetherteam/aetherii/client/renderer/entity/model/MoaBabyModel.java, used under the GNU Lesser General Public License v3.0 (docs/licenses/Aether-II-LGPL-3.0.txt).
 * Changed for Jaymo's Powers (2026-11): moved to this package, posed from our own render state.
 * This file stays under the LGPL v3.0.
 */
package com.jaymospowers.client.aether.mob;


import com.jaymospowers.client.aether.anim.MoaBabyAnimation;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.util.Mth;

final class MoaBabyModel extends AetherAnimalModel {
	private final KeyframeAnimation walkAnimation;
	private final KeyframeAnimation sitAnimation;
	private final KeyframeAnimation flyAnimation;
	private final ModelPart body_main;
	private final ModelPart tail;
	private final ModelPart leg_l_1;
	private final ModelPart leg_l_2;
	private final ModelPart head;
	private final ModelPart feather_1;
	private final ModelPart feather_2;
	private final ModelPart feather_3;
	private final ModelPart feather_4;
	private final ModelPart wing_l;
	private final ModelPart wing_r;
	private final ModelPart leg_r_1;
	private final ModelPart leg_r_2;
	private final ModelPart neck;

	private MoaBabyModel(ModelPart root) {
		super(root);
		this.walkAnimation = MoaBabyAnimation.WALK.bake(root);
		this.sitAnimation = MoaBabyAnimation.SIT.bake(root);
		this.flyAnimation = MoaBabyAnimation.FLY.bake(root);
		this.body_main = root.getChild("body_main");
		this.tail = this.body_main.getChild("tail");
		this.leg_l_1 = this.body_main.getChild("leg_l_1");
		this.leg_l_2 = this.leg_l_1.getChild("leg_l_2");
		this.head = this.body_main.getChild("head");
		this.feather_1 = this.head.getChild("feather_1");
		this.feather_2 = this.head.getChild("feather_2");
		this.feather_3 = this.head.getChild("feather_3");
		this.feather_4 = this.head.getChild("feather_4");
		this.wing_l = this.body_main.getChild("wing_l");
		this.wing_r = this.body_main.getChild("wing_r");
		this.leg_r_1 = this.body_main.getChild("leg_r_1");
		this.leg_r_2 = this.leg_r_1.getChild("leg_r_2");
		this.neck = this.body_main.getChild("neck");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition body_main = partdefinition.addOrReplaceChild("body_main", CubeListBuilder.create().texOffs(0, 16).mirror().addBox(-3.0F, -1.5F, -3.5F, 6.0F, 6.0F, 9.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, 15.0F, 0.0F));

		PartDefinition tail = body_main.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(26, 25).mirror().addBox(-2.5F, 0.0F, 0.0F, 5.0F, 1.0F, 6.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, -1.0F, 5.5F, -1.0472F, 0.0F, 0.0F));

		PartDefinition leg_l_1 = body_main.addOrReplaceChild("leg_l_1", CubeListBuilder.create().texOffs(50, 14).mirror().addBox(-2.0F, -2.0F, -2.1F, 3.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-2.5F, 2.0F, 2.5F, -0.7285F, 0.0F, 0.0F));

		PartDefinition leg_l_2 = leg_l_1.addOrReplaceChild("leg_l_2", CubeListBuilder.create().texOffs(56, 24).mirror().addBox(-1.5F, 0.2F, 0.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 1.0F, 0.0F, 0.6981F, 0.0F, 0.0F));

		PartDefinition head = body_main.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(-2.5F, -3.7F, -5.4F, 5.0F, 5.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offset(0.0F, -4.0F, -2.5F));

		PartDefinition feather_1 = head.addOrReplaceChild("feather_1", CubeListBuilder.create().texOffs(21, 16).mirror().addBox(-2.3F, -0.5F, 1.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.2F, -0.0873F, -0.2618F, 0.0F));

		PartDefinition feather_2 = head.addOrReplaceChild("feather_2", CubeListBuilder.create().texOffs(21, 12).mirror().addBox(-2.3F, -3.0F, 1.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.2F, 0.0873F, -0.2618F, 0.0F));

		PartDefinition feather_3 = head.addOrReplaceChild("feather_3", CubeListBuilder.create().texOffs(1, 12).mirror().addBox(1.3F, -3.0F, 1.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.2F, 0.0873F, 0.2618F, 0.0F));

		PartDefinition feather_4 = head.addOrReplaceChild("feather_4", CubeListBuilder.create().texOffs(1, 16).mirror().addBox(1.3F, -0.5F, 1.0F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.2F, -0.0873F, 0.2618F, 0.0F));

		PartDefinition wing_l = body_main.addOrReplaceChild("wing_l", CubeListBuilder.create().texOffs(48, 3).mirror().addBox(-1.0F, -2.0F, -1.0F, 1.0F, 4.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-3.0F, 1.0F, -1.9F, 0.0F, -0.4363F, 0.0F));

		PartDefinition wing_r = body_main.addOrReplaceChild("wing_r", CubeListBuilder.create().texOffs(32, 3).mirror().addBox(0.0F, -2.0F, -1.0F, 1.0F, 4.0F, 7.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(3.0F, 1.0F, -1.9F, 0.0F, 0.4363F, 0.0F));

		PartDefinition leg_r_1 = body_main.addOrReplaceChild("leg_r_1", CubeListBuilder.create().texOffs(36, 14).mirror().addBox(-1.0F, -2.0F, -2.1F, 3.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(2.5F, 2.0F, 2.5F, -0.6981F, 0.0F, 0.0F));

		PartDefinition leg_r_2 = leg_r_1.addOrReplaceChild("leg_r_2", CubeListBuilder.create().texOffs(48, 24).mirror().addBox(-0.5F, 0.2F, 0.0F, 2.0F, 6.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 1.0F, 0.0F, 0.6981F, 0.0F, 0.0F));

		PartDefinition neck = body_main.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(24, 1).mirror().addBox(-1.0F, -4.0F, -1.0F, 2.0F, 7.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, -1.0F, -3.0F, -0.1396F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 32);
	}

	static MoaBabyModel create() {
		return new MoaBabyModel(createBodyLayer().bakeRoot());
	}

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		this.head.xRot = state.xRot * Mth.DEG_TO_RAD;
		this.head.yRot = state.yRot * Mth.DEG_TO_RAD;
		// flapSpeed is 0 on the ground and 1 in the air (a chick flutters down)
		float flyAmount = Mth.clamp(state.flapSpeed, 0.0F, 1.0F);
		this.walkAnimation.applyWalk(state.walkAnimationPos, Mth.clamp(state.walkAnimationSpeed - flyAmount, 0.0F, 1.0F), 2.0F, 2.5F);
		this.flyAnimation.applyWalk(state.ageInTicks, flyAmount, 1.0F, 1.0F);
	}
}
