package com.jaymoskingdoms.client.monsters;

import java.util.List;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * the maggot, from {@code MoCModelMaggot}: its body stretching as it crawls
 * (of DrZhark's Mo' Creatures, GPL-3, see {@code licenses/mocreatures}), ported to 26.3 by {@code tools/gen_moc_small.py}:
 * its boxes and its animation as they were.
 */

final class MaggotModel extends EntityModel<BeastState> {
	private static final float RAD = 57.29578F;
	private final ModelPart Head, Body, Tail, Tailtip;

	MaggotModel(ModelPart root) {
		super(root);
		this.Head = root.getChild("Head");
		this.Body = root.getChild("Body");
		this.Tail = root.getChild("Tail");
		this.Tailtip = root.getChild("Tailtip");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_Head = root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 11).mirror(false).addBox(-1F, -1F, -2F, 2F, 2F, 2F), PartPose.offset(0F, 23F, -2F));
		PartDefinition p_Body = root.addOrReplaceChild("Body", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-1.5F, -2F, 0F, 3F, 3F, 4F), PartPose.offset(0F, 23F, -2F));
		PartDefinition p_Tail = root.addOrReplaceChild("Tail", CubeListBuilder.create().texOffs(0, 7).mirror(false).addBox(-1F, -1F, 0F, 2F, 2F, 2F), PartPose.offset(0F, 23F, 2F));
		PartDefinition p_Tailtip = root.addOrReplaceChild("Tailtip", CubeListBuilder.create().texOffs(8, 7).mirror(false).addBox(-0.5F, 0F, 0F, 1F, 1F, 1F), PartPose.offset(0F, 23F, 4F));
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(BeastState state) {
		super.setupAnim(state);
		rig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
	}

	private void rig(float f, float f1, float f2, float f3, float f4) {
		float stretch = 1.0F - Mth.cos(f * 3.0F) * f1 * 2.0F;
		this.Head.zScale = stretch;
		this.Body.zScale = stretch;
		this.Tail.zScale = stretch;
		this.Tailtip.zScale = stretch;
	}
}
