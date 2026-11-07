package com.jaymoskingdoms.client.monsters;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * (2026-11-05) A werewolf player's lean wolf ({@code WerewolfPlayers}' survivalist form, Andy: "a variation that you can
 * achieve"): our own boxes, a long-legged wolf on all fours, bigger than any dog, with a heavy ruff over the shoulders.
 * The look ({@code tools/gen_lean_wolf.py}) paints the texture from the same boxes. Its legs lope, its head follows the
 * look, and it runs low with its tail out when it's out for blood.
 */
final class LeanWolfModel extends EntityModel<LegendState> {
	private static final float DEG = Mth.DEG_TO_RAD;

	private final ModelPart body, ruff, head, tail, legFR, legFL, legHR, legHL;

	LeanWolfModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.ruff = root.getChild("ruff");
		this.head = root.getChild("head");
		this.tail = root.getChild("tail");
		this.legFR = root.getChild("leg_fr");
		this.legFL = root.getChild("leg_fl");
		this.legHR = root.getChild("leg_hr");
		this.legHL = root.getChild("leg_hl");
	}

	/** Keep in step with tools/gen_lean_wolf.py (the boxes and their texture corners). */
	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -9.0F, 8.0F, 8.0F, 18.0F),
			PartPose.offset(0.0F, 11.0F, 1.0F));
		root.addOrReplaceChild("ruff", CubeListBuilder.create().texOffs(0, 26).addBox(-5.0F, -5.0F, -4.0F, 10.0F, 10.0F, 8.0F),
			PartPose.offset(0.0F, 10.0F, -6.0F));
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(36, 26).addBox(-3.5F, -3.5F, -5.0F, 7.0F, 7.0F, 6.0F),
			PartPose.offset(0.0F, 7.5F, -10.0F));
		head.addOrReplaceChild("snout", CubeListBuilder.create().texOffs(36, 39).addBox(-2.0F, -0.5F, -9.0F, 4.0F, 4.0F, 5.0F), PartPose.ZERO);
		head.addOrReplaceChild("ear_r", CubeListBuilder.create().texOffs(54, 39).addBox(-3.0F, -6.5F, -2.0F, 2.0F, 3.0F, 1.0F), PartPose.ZERO);
		head.addOrReplaceChild("ear_l", CubeListBuilder.create().texOffs(54, 43).addBox(1.0F, -6.5F, -2.0F, 2.0F, 3.0F, 1.0F), PartPose.ZERO);
		root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(48, 48).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F),
			PartPose.offsetAndRotation(0.0F, 8.5F, 9.5F, 0.9F, 0.0F, 0.0F));
		root.addOrReplaceChild("leg_fr", CubeListBuilder.create().texOffs(0, 48).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F),
			PartPose.offset(-2.5F, 13.0F, -6.0F));
		root.addOrReplaceChild("leg_fl", CubeListBuilder.create().texOffs(12, 48).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F),
			PartPose.offset(2.5F, 13.0F, -6.0F));
		root.addOrReplaceChild("leg_hr", CubeListBuilder.create().texOffs(24, 48).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F),
			PartPose.offset(-2.5F, 13.0F, 7.5F));
		root.addOrReplaceChild("leg_hl", CubeListBuilder.create().texOffs(36, 48).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 11.0F, 3.0F),
			PartPose.offset(2.5F, 13.0F, 7.5F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(LegendState state) {
		super.setupAnim(state);
		float pos = state.walkAnimationPos * 0.6662F, speed = Math.min(1.0F, state.walkAnimationSpeed);
		// (a lope: front and hind pairs out of step, wider at a run)
		float stride = state.sprinting ? 1.6F : 1.2F;
		this.legFR.xRot = Mth.cos(pos) * stride * speed;
		this.legFL.xRot = Mth.cos(pos + Mth.PI) * stride * speed;
		this.legHR.xRot = Mth.cos(pos + Mth.PI * 0.5F) * stride * speed;
		this.legHL.xRot = Mth.cos(pos + Mth.PI * 1.5F) * stride * speed;
		this.head.yRot = Mth.clamp(state.yRot, -60.0F, 60.0F) * DEG;
		this.head.xRot = Mth.clamp(state.xRot, -40.0F, 50.0F) * DEG;
		// (out for blood: head low, tail straight out behind)
		if (state.hunched) {
			this.head.y += 1.5F;
			this.head.xRot += 0.25F;
			this.tail.xRot = 1.35F;
		} else {
			this.tail.xRot = 0.9F + Mth.cos(pos) * 0.15F * speed;
		}
		this.tail.yRot = Mth.sin(state.ageInTicks * 0.15F) * 0.12F;
		// (the body bobs with the stride)
		float bob = Mth.abs(Mth.sin(pos)) * speed * 0.6F;
		this.body.y -= bob;
		this.ruff.y -= bob;
		this.head.y -= bob;
		this.tail.y -= bob;
	}
}
