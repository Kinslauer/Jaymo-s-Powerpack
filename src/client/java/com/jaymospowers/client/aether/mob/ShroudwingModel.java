package com.jaymospowers.client.aether.mob;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The shroudwing: a round-backed little beetle with hard wing cases and thin wings that open when it takes off. */
final class ShroudwingModel extends AetherAnimalModel {
	private final ModelPart elytraL, elytraR, wingL, wingR;

	private ShroudwingModel(ModelPart root) {
		super(root);
		this.elytraL = root.getChild("elytra_l");
		this.elytraR = root.getChild("elytra_r");
		this.wingL = root.getChild("swing_l");
		this.wingR = root.getChild("swing_r");
	}

	static ShroudwingModel create() {
		return new ShroudwingModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		PartDefinition body = part(r, "sbody", 0, 0, -2F, -1.5F, -3F, 4, 3, 6, 0F, 21F, 0F);
		part(body, "shead", 22, 0, -1.5F, -1F, -2.5F, 3, 2, 2, 0F, 0F, -3F);
		part(r, "elytra_l", 0, 9, 0F, 0F, 0F, 2, 1, 6, 0F, 19.5F, -3F);
		part(r, "elytra_r", 16, 9, -2F, 0F, 0F, 2, 1, 6, 0F, 19.5F, -3F);
		part(r, "swing_l", 0, 16, 0F, 0F, -1F, 6, 0, 8, 1F, 19.5F, -2F);
		part(r, "swing_r", 0, 24, -6F, 0F, -1F, 6, 0, 8, -1F, 19.5F, -2F);
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(AetherAnimalRenderer.State state) {
		super.setupAnim(state);
		boolean flying = !state.resting;
		// in the air the cases lift and the wings beat; at rest everything lies shut
		this.elytraL.zRot = flying ? -0.6F : 0.0F;
		this.elytraR.zRot = flying ? 0.6F : 0.0F;
		float beat = flying ? Mth.sin(state.ageInTicks * 2.0F) * 0.8F : 0.0F;
		this.wingL.zRot = flying ? -0.3F - beat : 0.0F;
		this.wingR.zRot = flying ? 0.3F + beat : 0.0F;
		this.wingL.visible = flying;
		this.wingR.visible = flying;
	}
}
