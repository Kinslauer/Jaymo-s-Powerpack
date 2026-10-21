package com.jaymospowers.client.aether.dungeon;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * What the Sentry Ruins' models share: how a part is declared and the walk. Every part is one {@code part(...)} call on a
 * single line (parent, name, texture u and v, box x y z and w h d, pivot x y z): tools/gen_aether_dungeon.py reads those lines
 * to paint each skin on the right faces. y is down, the feet are at y 24, and the mob faces -z.
 */
abstract class DungeonModel extends EntityModel<DungeonRenderer.State> {
	static final float DEG = Mth.DEG_TO_RAD;

	DungeonModel(ModelPart root) {
		super(root);
	}

	static PartDefinition part(PartDefinition parent, String name, int u, int v, float x, float y, float z, int w, int h, int d,
		float px, float py, float pz) {
		return parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d), PartPose.offset(px, py, pz));
	}

	/** A pair of legs swinging in opposition. */
	static void swing(ModelPart a, ModelPart b, DungeonRenderer.State state, float amount) {
		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		a.xRot = Mth.cos(state.walkAnimationPos * 0.6662F) * amount * speed;
		b.xRot = Mth.cos(state.walkAnimationPos * 0.6662F + Mth.PI) * amount * speed;
	}

	static void look(ModelPart head, DungeonRenderer.State state) {
		head.xRot = state.xRot * DEG;
		head.yRot = state.yRot * DEG;
	}
}
