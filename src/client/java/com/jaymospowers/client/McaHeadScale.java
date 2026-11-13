package com.jaymospowers.client;

import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.client.render.VillagerVisuals;

/** MCA's head size for the body drawn with a render state ({@link WornPose}); 1 for any other. Only loaded with MCA. */
final class McaHeadScale {
	private McaHeadScale() {
	}

	static float of(Object state) {
		if (!(state instanceof VillagerStateHolder holder) || !holder.mca$isGeneticsRendererActive()) {
			return 1.0F;
		}
		VillagerVisuals visuals = holder.mca$getVisuals();
		return visuals == null ? 1.0F : visuals.dimensions().getHead();
	}
}
