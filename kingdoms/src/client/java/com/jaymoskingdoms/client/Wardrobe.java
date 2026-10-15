package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Tailors;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/**
 * (0.7.55) A tailor's fitting: when one names their fee ({@link Tailors}), MCA's editor opens on its clothing page only
 * ({@link WardrobeScreen}). Needs MCA.
 */
public final class Wardrobe {
	private Wardrobe() {
	}

	static void register() {
		if (!FabricLoader.getInstance().isModLoaded("mca")) {
			return;
		}
		ClientPlayNetworking.registerGlobalReceiver(Tailors.Open.TYPE, (payload, context) -> {
			if (context.client().player != null) {
				context.client().gui.setScreen(new WardrobeScreen(context.client().player.getUUID(), payload.fee()));
			}
		});
	}
}
