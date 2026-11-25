package com.jaymoskingdoms.client.dragons;

import com.jaymoskingdoms.dragons.Wyverns;
import net.minecraft.client.renderer.entity.EntityRenderers;

/** The wyvern's renderer: Mo' Creatures' wyvern model ({@link WyvernModel}) in one of its four hides, as the wind dragons are drawn. */
public final class WyvernClient {
	/** Its shadow's radius and the scale it is drawn at over its model (blocks). */
	static final float SHADOW = 1.5F, SCALE = 0.62F, REACH = 7.0F;

	private WyvernClient() {
	}

	public static void register() {
		EntityRenderers.register(Wyverns.WYVERN, c -> new WindDragonRenderer<>(c, "wyvern", new WyvernModel(WyvernModel.createBodyLayer().bakeRoot()), SHADOW, SCALE, REACH));
	}
}
