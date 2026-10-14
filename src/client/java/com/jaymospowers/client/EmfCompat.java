package com.jaymospowers.client;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Entity Model Features (LGPL-3.0, by Traben; compiled against, touched only from {@link EmfAccess} and only when it's
 * loaded) runs Fresh Animations: Player Extension's animations on the player model. They move the limbs' pivots as well
 * as turning them, so while one of our own poses is on a player EMF pauses its animations for that player, the way it
 * already does for Better Combat's swings (through Player Animation Library) and for emotes.
 */
final class EmfCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("entity_model_features");

	private EmfCompat() {
	}

	static void init() {
		if (LOADED) {
			EmfAccess.registerPause();
		}
	}
}
