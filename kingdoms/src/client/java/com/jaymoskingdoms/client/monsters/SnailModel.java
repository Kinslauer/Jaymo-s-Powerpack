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
 * the snail, from {@code MoCModelSnail}: gliding on its foot, its shell pulled down over it when it hides
 * (of DrZhark's Mo' Creatures, GPL-3, see {@code licenses/mocreatures}), ported to 26.3 by {@code tools/gen_moc_small.py}:
 * its boxes and its animation as they were.
 */

final class SnailModel extends EntityModel<BeastState> {
	private static final float RAD = 57.29578F;
	private final ModelPart Head, Antenna, Body, ShellUp, ShellDown, Tail;
	private boolean hiding;

	SnailModel(ModelPart root) {
		super(root);
		this.Head = root.getChild("Head");
		this.Antenna = root.getChild("Antenna");
		this.Body = root.getChild("Body");
		this.ShellUp = root.getChild("ShellUp");
		this.ShellDown = root.getChild("ShellDown");
		this.Tail = root.getChild("Tail");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_Head = root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 6).mirror(false).addBox(-1F, 0F, -1F, 2F, 2F, 2F), PartPose.offsetAndRotation(0F, 21.8F, -1F, -0.4537856F, 0F, 0F));
		PartDefinition p_Antenna = root.addOrReplaceChild("Antenna", CubeListBuilder.create().texOffs(8, 0).mirror(false).addBox(-1.5F, 0F, -1F, 3F, 2F, 0F), PartPose.offsetAndRotation(0F, 19.4F, -1F, 0.0523599F, 0F, 0F));
		PartDefinition p_Body = root.addOrReplaceChild("Body", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-1F, 0F, -1F, 2F, 2F, 4F), PartPose.offset(0F, 22F, 0F));
		PartDefinition p_ShellUp = root.addOrReplaceChild("ShellUp", CubeListBuilder.create().texOffs(12, 0).mirror(false).addBox(-1F, -3F, 0F, 2F, 3F, 3F), PartPose.offsetAndRotation(0F, 22.3F, -0.2F, 0.2268928F, 0F, 0F));
		PartDefinition p_ShellDown = root.addOrReplaceChild("ShellDown", CubeListBuilder.create().texOffs(12, 0).mirror(false).addBox(-1F, 0F, 0F, 2F, 3F, 3F), PartPose.offset(0F, 21F, 0F));
		PartDefinition p_Tail = root.addOrReplaceChild("Tail", CubeListBuilder.create().texOffs(1, 2).mirror(false).addBox(-1F, 0F, 0F, 2F, 1F, 3F), PartPose.offset(0F, 23F, 3F));
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(BeastState state) {
		super.setupAnim(state);
		this.hiding = state.hiding;
		for (ModelPart p : List.of(ShellDown)) {
			p.visible = this.hiding;
		}
		for (ModelPart p : List.of(Head, Antenna, Body, ShellUp, Tail)) {
			p.visible = !this.hiding;
		}
		rig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
	}

	/**
	 * Andy: snails "should slide". It glides on its foot: while it moves, a slow wave runs along it, the tail drawing out
	 * and in and the shell easing with it, with nothing lifting off the ground (the old rig flicked the tail back and
	 * forth). It eases in and out as it starts and stops, so it doesn't snap.
	 */
	private void rig(float f, float f1, float f2, float f3, float f4) {
		float going = Mth.clamp(f1 * 4.0F, 0.0F, 1.0F);
		float wave = Mth.sin(f2 * 0.12F) * 0.5F + 0.5F;
		this.Tail.z = 2.4F + wave * 1.2F * going;
		this.Head.z = -1.0F - wave * 0.4F * going;
		this.ShellUp.xRot = 0.2268928F + (wave - 0.5F) * 0.06F * going;
	}
}
