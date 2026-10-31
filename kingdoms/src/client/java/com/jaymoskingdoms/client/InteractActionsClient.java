package com.jaymoskingdoms.client;

import com.jaymoskingdoms.InteractActions;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * (0.7.449) Which of the Interact page's actions ({@link InteractActions}) the villager you last right-clicked offers
 * you, as the server sent them ahead of MCA's screen; {@code McaActionButtonsMixin} hides the rest.
 */
public final class InteractActionsClient {
	private static int entityId = -1;
	private static List<String> actions = List.of();

	private InteractActionsClient() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(InteractActions.Offered.TYPE, (payload, context) -> {
			entityId = payload.entityId();
			actions = List.copyOf(payload.actions());
		});
	}

	/** {@code entity} offers {@code action} (as last heard). */
	public static boolean offers(int entity, String action) {
		return entity == entityId && actions.contains(action);
	}
}
