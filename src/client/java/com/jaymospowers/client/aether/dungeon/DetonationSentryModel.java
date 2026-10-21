package com.jaymospowers.client.aether.dungeon;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The detonation sentry: a stone cube on four stubby legs with a lid on its head; it swells and shakes as the fuse burns. */
final class DetonationSentryModel extends DungeonModel {
	private final ModelPart body, lid;
	private final ModelPart[] legs = new ModelPart[4];

	private DetonationSentryModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.lid = root.getChild("lid");
		for (int i = 0; i < 4; i++) {
			this.legs[i] = root.getChild("leg_" + i);
		}
	}

	static DetonationSentryModel create() {
		return new DetonationSentryModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "body", 0, 0, -7.0F, -13.0F, -7.0F, 14, 9, 14, 0.0F, 24.0F, 0.0F);
		part(r, "lid", 0, 24, -3.0F, -15.0F, -3.0F, 6, 2, 6, 0.0F, 24.0F, 0.0F);
		part(r, "leg_0", 0, 34, -2.0F, 0.0F, -2.0F, 4, 4, 4, -4.5F, 20.0F, -4.5F);
		part(r, "leg_1", 16, 34, -2.0F, 0.0F, -2.0F, 4, 4, 4, 4.5F, 20.0F, -4.5F);
		part(r, "leg_2", 32, 34, -2.0F, 0.0F, -2.0F, 4, 4, 4, -4.5F, 20.0F, 4.5F);
		part(r, "leg_3", 48, 34, -2.0F, 0.0F, -2.0F, 4, 4, 4, 4.5F, 20.0F, 4.5F);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(DungeonRenderer.State state) {
		super.setupAnim(state);
		float run = Math.min(state.walkAnimationSpeed, 1.0F);
		this.legs[0].xRot = Mth.cos(state.walkAnimationPos * 0.9F) * 0.9F * run;
		this.legs[3].xRot = this.legs[0].xRot;
		this.legs[1].xRot = -this.legs[0].xRot;
		this.legs[2].xRot = -this.legs[0].xRot;
		// a lit fuse makes it tremble, harder as it burns
		if (state.progress > 0.0F) {
			float shake = Mth.sin(state.ageInTicks * 2.2F) * 0.03F * (0.5F + state.progress);
			this.body.zRot = shake;
			this.lid.zRot = shake;
			this.lid.y = 24.0F - state.progress * 2.0F;
		}
		this.body.yRot = state.awake ? 0.0F : Mth.sin(state.ageInTicks * 0.05F) * 0.02F;
	}
}
