package com.jaymospowers.client;

import traben.entity_model_features.EMFAnimationApi;

/** The only class that touches Entity Model Features (see EmfCompat); loaded only when EMF is. */
final class EmfAccess {
	private EmfAccess() {
	}

	static void registerPause() {
		try {
			// EMF asks this right after the model's own animation (LivingEntityRenderer.submit), and again as each model is
			// finally drawn, after the renderer has moved on: then only the entity tells us whether it's posed
			EMFAnimationApi.registerPauseCondition(entity -> PoseTakeover.drawingPosed() || PoseTakeover.posedEntity(entity));
		} catch (Exception e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't hook into Entity Model Features; its player animations may fight our poses", e);
		}
	}
}
