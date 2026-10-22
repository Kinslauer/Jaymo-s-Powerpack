package com.jaymospowers.client.aether.dungeon;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** The sentry golem: a tall, stiff figure of carved stone with a satchel of bombs on its back; its right arm goes up to lob one. */
final class SentryGolemModel extends DungeonModel {
	private final ModelPart head, armL, armR, legL, legR;

	private SentryGolemModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.armL = root.getChild("arm_l");
		this.armR = root.getChild("arm_r");
		this.legL = root.getChild("leg_l");
		this.legR = root.getChild("leg_r");
	}

	static SentryGolemModel create() {
		return new SentryGolemModel(layer().bakeRoot());
	}

	private static LayerDefinition layer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		part(r, "head", 0, 0, -4.0F, -8.0F, -4.0F, 8, 8, 8, 0.0F, 5.0F, 0.0F);
		PartDefinition body = part(r, "body", 0, 16, -5.0F, 0.0F, -3.0F, 10, 12, 6, 0.0F, 5.0F, 0.0F);
		part(body, "satchel", 0, 34, -4.0F, 1.0F, 0.0F, 8, 7, 4, 0.0F, 0.0F, 3.0F);
		part(r, "arm_l", 32, 0, -1.0F, -1.0F, -2.0F, 4, 12, 4, 5.0F, 6.0F, 0.0F);
		part(r, "arm_r", 48, 0, -3.0F, -1.0F, -2.0F, 4, 12, 4, -5.0F, 6.0F, 0.0F);
		part(r, "leg_l", 32, 16, -2.0F, 0.0F, -2.0F, 4, 13, 4, 2.0F, 11.0F, 0.0F);
		part(r, "leg_r", 48, 16, -2.0F, 0.0F, -2.0F, 4, 13, 4, -2.0F, 11.0F, 0.0F);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(DungeonRenderer.State state) {
		super.setupAnim(state);
		look(this.head, state);
		swing(this.legL, this.legR, state, 0.8F);
		float walk = Math.min(state.walkAnimationSpeed, 1.0F);
		this.armL.xRot = Mth.cos(state.walkAnimationPos * 0.6662F + Mth.PI) * 0.6F * walk;
		this.armR.xRot = Mth.cos(state.walkAnimationPos * 0.6662F) * 0.6F * walk;
		// winding up a throw: the arm swings back and up, and comes over as the bomb leaves
		if (state.progress > 0.0F) {
			float wind = Mth.clamp(state.progress, 0.0F, 1.0F);
			this.armR.xRot = -2.6F * wind;
			this.armR.zRot = -0.15F;
		}
		float blow = Mth.sin(state.attack * Mth.PI);
		this.armR.xRot -= blow * 1.6F;
		this.armL.xRot -= blow * 1.6F;
	}
}
