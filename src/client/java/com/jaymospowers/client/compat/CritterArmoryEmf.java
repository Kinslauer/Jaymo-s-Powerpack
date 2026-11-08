package com.jaymospowers.client.compat;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.utils.EMFAnimationPauseHandler;

/** The only class of the Critter Armory compat that touches Entity Model Features (see CritterArmoryFreshAnimationsCompat). */
final class CritterArmoryEmf {
	private static boolean broken;

	private CritterArmoryEmf() {
	}

	/** Whether this model is one Entity Model Features drives (its root is EMF's), as opposed to a plain armour model. */
	static boolean isEmfModel(EntityModel<?> model) {
		return model.root() instanceof EMFModelPartRoot;
	}

	/**
	 * Whether EMF has just animated this body: it has animations and nothing paused them (our poses, Better Combat's
	 * swings and emotes do; then the pose those are laid over stands). Mirrors EmfAccess#animatedNow for any model.
	 */
	static boolean animatedNow(EntityRenderState state, EntityModel<?> body) {
		if (broken || !(body.root() instanceof EMFModelPartRoot root) || !root.hasAnimation()) {
			return false;
		}
		try {
			EMFEntityRenderState emf = EMFEntityRenderState.from(state);
			return emf != null && !EMFAnimationPauseHandler.shouldAnimationsPause(emf);
		} catch (Throwable e) {
			broken = true;
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't ask Entity Model Features about an animal's pose; Critter Armory's armour keeps the plain one", e);
			return false;
		}
	}
}
