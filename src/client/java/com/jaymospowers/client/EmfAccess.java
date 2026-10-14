package com.jaymospowers.client;

import traben.entity_model_features.EMFAnimationApi;

/** The only class that touches Entity Model Features (see EmfCompat); loaded only when EMF is. */
final class EmfAccess {
	private EmfAccess() {
	}

	static void registerPause() {
		try {
			// EMF asks this while drawing an entity, right after the model's own animation (LivingEntityRenderer.submit)
			EMFAnimationApi.registerPauseCondition(entity -> PoseTakeover.drawingPosed());
		} catch (Exception e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't hook into Entity Model Features; its player animations may fight our poses", e);
		}
	}
}
