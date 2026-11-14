package com.jaymoskingdoms.client;

import com.jaymoskingdoms.HordeDays;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** A horde day's grey sky on this client ({@link HordeDays}): told by the server, faded in and out. */
final class HordeDaysClient {
	private HordeDaysClient() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(HordeDays.Sync.TYPE, (payload, context) -> HordeDays.clientOvercast = payload.overcast());
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			float step = 1.0F / HordeDays.FADE_TICKS;
			HordeDays.clientFade = HordeDays.clientOvercast ? Math.min(1.0F, HordeDays.clientFade + step) : Math.max(0.0F, HordeDays.clientFade - step);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			HordeDays.clientOvercast = false;
			HordeDays.clientFade = 0.0F;
		});
	}
}
