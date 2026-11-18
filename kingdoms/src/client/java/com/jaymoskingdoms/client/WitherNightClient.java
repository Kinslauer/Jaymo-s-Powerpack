package com.jaymoskingdoms.client;

import com.jaymoskingdoms.wither.WitherNight;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The client's side of the Wither's night: told whether a Wither lives, DarkNights holds the sky dark until it dies. */
final class WitherNightClient {
	private WitherNightClient() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(WitherNight.Sync.TYPE, (payload, context) -> WitherNight.clientOn = payload.on());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WitherNight.clientOn = false);
	}
}
