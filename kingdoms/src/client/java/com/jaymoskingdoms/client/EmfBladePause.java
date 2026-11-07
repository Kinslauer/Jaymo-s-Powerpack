package com.jaymoskingdoms.client;

import traben.entity_model_features.EMFAnimationApi;

/**
 * (0.7.135) The only class that touches Entity Model Features for the hidden blade (loaded only when EMF is): its
 * animations (Fresh Animations' idle arms) pause for a player whose blade is springing or out, so the guard holds;
 * (0.7.246) and for a ship's crew at their stations; (0.7.472) and for you while the life story draws you ({@link StoryFigure#drawingYou}).
 */
final class EmfBladePause {
	private EmfBladePause() {
	}

	static void register() {
		try {
			EMFAnimationApi.registerPauseCondition(HiddenBladeClient::guarding);
			// (0.7.246) and for a ship's crew at their stations, who stand rather than sit
			EMFAnimationApi.registerPauseCondition(ShipClient::crewAboard);
			// (0.7.472) and for you while the life story draws you: the figure acts out its scene, not Fresh Animations' flying
			EMFAnimationApi.registerPauseCondition(StoryFigure::drawingYou);
		} catch (Exception e) {
			com.jaymoskingdoms.JaymosKingdoms.LOGGER.warn("Couldn't hook into Entity Model Features; its player animations may hide the hidden blade's guard", e);
		}
	}
}
