package com.jaymospowers.client.aether.mob;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * What the Aether animals' models share. The models themselves are The Aether II's (LGPL-3.0, see each file's header and
 * docs/aether.md); tools/gen_aether2_skins.py reads the boxes they declare to paint each skin. y is down, the feet are
 * at y 24, and the mob faces -z.
 */
abstract class AetherAnimalModel extends EntityModel<AetherAnimalRenderer.State> {
	static final float DEG = Mth.DEG_TO_RAD;

	AetherAnimalModel(ModelPart root) {
		super(root);
	}

	/** A pair of legs swinging in opposition (the walk), {@code amount} radians at full stride. */
	static void swing(ModelPart a, ModelPart b, AetherAnimalRenderer.State state, float amount) {
		float speed = Math.min(state.walkAnimationSpeed, 1.0F);
		a.xRot = Mth.cos(state.walkAnimationPos * 0.6662F) * amount * speed;
		b.xRot = Mth.cos(state.walkAnimationPos * 0.6662F + Mth.PI) * amount * speed;
	}

	/** How far a winged mount lowers the wing on the side a rider climbs up (radians from upright, each way). */
	static final float WING_RAMP = 75.0F * Mth.DEG_TO_RAD;

	/**
	 * A winged mount being climbed lowers the wing on the climber's side out to the ground like a ramp. The wings'
	 * resting angles are (left) negative and (right) positive about z, so the lowered ones go further the same way.
	 */
	static void lowerWing(ModelPart left, ModelPart right, AetherAnimalRenderer.State state) {
		if (state.climb <= 0.0F) {
			return;
		}
		if (state.climbSide <= 0) {
			left.zRot = Mth.lerp(state.climb, left.zRot, -WING_RAMP);
		}
		if (state.climbSide >= 0) {
			right.zRot = Mth.lerp(state.climb, right.zRot, WING_RAMP);
		}
	}

	/** The head following where the mob looks. */
	static void look(ModelPart head, AetherAnimalRenderer.State state) {
		head.xRot = state.xRot * DEG;
		head.yRot = state.yRot * DEG;
	}
}
