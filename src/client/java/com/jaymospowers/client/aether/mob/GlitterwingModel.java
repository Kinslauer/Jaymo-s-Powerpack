package com.jaymospowers.client.aether.mob;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The glitterwing: a slim body with two broad flat wings that beat fast in the air and fold back when it rests. The orange mimic holds its wings turned. */
final class GlitterwingModel extends AetherAnimalModel {
	/** The variant whose wings are turned 40 degrees. */
	static final int ORANGE_MIMIC = 6;

	private final ModelPart wingL, wingR;

	private GlitterwingModel(ModelPart root) {
		super(root);
		this.wingL = root.getChild("wing_l");
		this.wingR = root.getChild("wing_r");
	}

	static GlitterwingModel create() {
		return new GlitterwingModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		PartDefinition body = part(r, "body", 0, 0, -1F, -1F, -3F, 2, 2, 7, 0F, 18F, 0F);
		part(body, "head", 18, 0, -1.5F, -1.5F, -3F, 3, 3, 3, 0F, 0F, -3F);
		part(r, "wing_l", 0, 9, 0F, 0F, -3F, 10, 0, 6, 1F, 17F, 0F);
		part(r, "wing_r", 0, 15, -10F, 0F, -3F, 10, 0, 6, -1F, 17F, 0F);
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		float beat = state.resting ? 0.0F : Mth.sin(state.ageInTicks * 1.8F) * 0.7F;
		float rest = state.resting ? 0.45F : 0.0F;
		float turn = state.variant == ORANGE_MIMIC ? 40.0F * DEG : 0.0F;
		this.wingL.zRot = -beat - rest;
		this.wingR.zRot = beat + rest;
		this.wingL.yRot = turn;
		this.wingR.yRot = -turn;
	}
}
