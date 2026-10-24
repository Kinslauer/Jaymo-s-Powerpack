package com.jaymospowers.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * Entity Model Features (LGPL-3.0, by Traben; compiled against, touched only from {@link EmfAccess} and only when it's
 * loaded) runs Fresh Animations: Player Extension's animations on the player model. They move the limbs' pivots as well
 * as turning them, so while one of our own poses is on a player EMF pauses its animations for that player, the way it
 * already does for Better Combat's swings (through Player Animation Library) and for emotes.
 */
public final class EmfCompat {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("entity_model_features");

	private EmfCompat() {
	}

	/**
	 * (0.11.78) Whether EMF has just animated {@code body} for {@code state} (called just before the render layers): then
	 * the pose its parts hold is Fresh Animations', which models drawn over the body must take (CostumeBody).
	 */
	public static boolean bodyAnimated(EntityRenderState state, Model<?> body) {
		return LOADED && body instanceof HumanoidModel<?> humanoid && EmfAccess.animatedNow(state, humanoid);
	}

	/**
	 * (0.15.147) Keeps EMF paused for a player while one of our poses is on, by EMF's own per-player pause, which nothing
	 * lifts. The pause condition alone isn't enough with Touch'n Motion: its EMF Compat Core lifts EMF's pause for a player
	 * any of its addons holds a pose for (Touch'n Motion's riding pose), and Fresh Animations would come back over ours.
	 * Paused, Touch'n Motion's own gestures step aside too (they run inside EMF's animation).
	 */
	static void hold(int id, boolean posed) {
		if (LOADED) {
			EmfAccess.hold(id, posed);
		}
	}

	static void init() {
		if (LOADED) {
			EmfAccess.registerPause();
		}
	}
}
