package com.jaymospowers.client;

import com.jaymospowers.client.armory.TaczFirstPerson;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.animation.state.EMFState;

/** The only class that touches Entity Model Features (see EmfCompat); loaded only when EMF is. */
final class EmfAccess {
	private EmfAccess() {
	}

	static void registerPause() {
		try {
			// EMF asks this right after the model's own animation (LivingEntityRenderer.submit), and again as each model is
			// finally drawn, after the renderer has moved on: then only the entity tells us whether it's posed. In first
			// person (EMF's isInHand, the whole hands pass) a TaCZ gun's arms are left to the gun (TaczFirstPerson).
			EMFAnimationApi.registerPauseCondition(entity -> PoseTakeover.drawingPosed() || PoseTakeover.posedEntity(entity)
				|| EMFState.isInHand && TaczFirstPerson.holdingGun(entity));
		} catch (Exception e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't hook into Entity Model Features; its player animations may fight our poses", e);
		}
	}
}
