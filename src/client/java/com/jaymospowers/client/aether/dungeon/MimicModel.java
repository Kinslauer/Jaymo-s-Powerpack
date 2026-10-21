package com.jaymospowers.client.aether.dungeon;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The mimic: a sentry crate on four short legs, its lid hinged at the back and a long tongue inside; the lid snaps as it bites. */
final class MimicModel extends DungeonModel {
	private final ModelPart lid, tongue;
	private final ModelPart[] legs = new ModelPart[4];

	private MimicModel(ModelPart root) {
		super(root);
		this.lid = root.getChild("lid");
		this.tongue = root.getChild("tongue");
		for (int i = 0; i < 4; i++) {
			this.legs[i] = root.getChild("leg_" + i);
		}
	}

	static MimicModel create() {
		return new MimicModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "base", 0, 0, -7.0F, -14.0F, -7.0F, 14, 8, 14, 0.0F, 24.0F, 0.0F);
		// the lid hangs from its back edge
		part(r, "lid", 0, 22, -7.0F, -5.0F, -14.0F, 14, 5, 14, 0.0F, 10.0F, 7.0F);
		part(r, "leg_0", 0, 42, -2.0F, 0.0F, -2.0F, 4, 6, 4, -4.5F, 18.0F, -4.5F);
		part(r, "leg_1", 16, 42, -2.0F, 0.0F, -2.0F, 4, 6, 4, 4.5F, 18.0F, -4.5F);
		part(r, "leg_2", 32, 42, -2.0F, 0.0F, -2.0F, 4, 6, 4, -4.5F, 18.0F, 4.5F);
		part(r, "leg_3", 48, 42, -2.0F, 0.0F, -2.0F, 4, 6, 4, 4.5F, 18.0F, 4.5F);
		part(r, "tongue", 0, 54, -2.0F, -1.0F, -4.0F, 4, 1, 8, 0.0F, 11.0F, -1.0F);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(DungeonRenderer.State state) {
		super.setupAnim(state);
		float run = Math.min(state.walkAnimationSpeed, 1.0F);
		this.legs[0].xRot = Mth.cos(state.walkAnimationPos * 0.9F) * 0.8F * run;
		this.legs[3].xRot = this.legs[0].xRot;
		this.legs[1].xRot = -this.legs[0].xRot;
		this.legs[2].xRot = -this.legs[0].xRot;
		// unfolding: the lid starts shut, flies up, and settles open a hand's width; a bite snaps it shut
		float open = state.progress;
		float lift = open < 1.0F ? Mth.sin(open * Mth.PI * 0.5F) * 1.6F : 0.5F + Mth.sin(state.ageInTicks * 0.3F) * 0.08F;
		float bite = Mth.sin(state.attack * Mth.PI);
		this.lid.xRot = -(lift - bite * 0.5F);
		this.tongue.xRot = Mth.sin(state.ageInTicks * 0.25F) * 0.1F;
		this.tongue.visible = open >= 1.0F;
	}
}
