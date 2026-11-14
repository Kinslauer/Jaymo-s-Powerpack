package com.jaymospowers.client.aether.monster;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * What the Aether's monsters' models share. The models themselves are The Aether II's (LGPL-3.0, see each file's header and
 * docs/aether.md); tools/gen_aether2_skins.py reads the boxes they declare to paint each skin. y is down, the feet are
 * at y 24, and the mob faces -z.
 */
abstract class AetherMonsterModel extends EntityModel<AetherMonsterRenderer.State> {
	static final float DEG = Mth.DEG_TO_RAD;

	AetherMonsterModel(ModelPart root) {
		super(root);
	}

	/** A pair of legs swinging in opposition (the walk), {@code amount} radians at full stride. */
	static void swing(ModelPart a, ModelPart b, AetherMonsterRenderer.State state, float amount) {
		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		a.xRot = Mth.cos(state.walkAnimationPos * 0.6662F) * amount * speed;
		b.xRot = Mth.cos(state.walkAnimationPos * 0.6662F + Mth.PI) * amount * speed;
	}

	/** The head following where the mob looks. */
	static void look(ModelPart head, AetherMonsterRenderer.State state) {
		head.xRot = state.xRot * DEG;
		head.yRot = state.yRot * DEG;
	}

	/** Eases {@code t} (0 to 1) in and out. */
	static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}
}
