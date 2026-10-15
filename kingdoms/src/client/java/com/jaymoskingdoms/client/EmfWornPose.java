package com.jaymoskingdoms.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;
import traben.entity_model_features.utils.EMFAnimationPauseHandler;

/** The only class that touches Entity Model Features (loaded only when it is); see WornModel. */
final class EmfWornPose {
	private static boolean broken;

	private EmfWornPose() {
	}

	/**
	 * Whether EMF has just animated this body (it has animations, and nothing paused them: Powers' poses, Better Combat's
	 * swings and emotes pause them, and then the armour keeps the vanilla pose those are laid over, as before).
	 */
	static boolean animatedNow(EntityRenderState state, HumanoidModel<?> body) {
		if (broken || !(body.root() instanceof EMFModelPartRoot root) || !root.hasAnimation()) {
			return false;
		}
		try {
			EMFEntityRenderState emf = EMFEntityRenderState.from(state);
			return emf != null && !EMFAnimationPauseHandler.shouldAnimationsPause(emf);
		} catch (Throwable e) {
			broken = true;
			com.jaymoskingdoms.JaymosKingdoms.LOGGER.warn("Armory: couldn't ask Entity Model Features about the body's pose; armour keeps the plain one", e);
			return false;
		}
	}
}
