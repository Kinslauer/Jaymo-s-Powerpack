package com.jaymospowers.client.aether.dungeon;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * What the Sentry Ruins' models share. The models themselves are The Aether II's (LGPL-3.0, see each file's header and
 * docs/aether.md); tools/gen_aether2_skins.py reads the boxes they declare to paint each skin. y is down, the feet are
 * at y 24, and the mob faces -z.
 */
abstract class DungeonModel extends EntityModel<DungeonRenderer.State> {
	static final float DEG = Mth.DEG_TO_RAD;

	DungeonModel(ModelPart root) {
		super(root);
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
