package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Moons;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** (0.6.6) The client's side of the lunar events: told of tonight's moon, it fades its colours in and out. */
final class MoonClient {
	private MoonClient() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Moons.Sync.TYPE, (payload, context) -> {
			Moons.Moon[] moons = Moons.Moon.values();
			Moons.clientMoon = payload.moon() >= 0 && payload.moon() < moons.length ? moons[payload.moon()] : Moons.Moon.NONE;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			Moons.clientMoon = Moons.Moon.NONE;
			Moons.clientShown = Moons.Moon.NONE;
			Moons.clientFade = 0.0F;
		});
	}

	private static void tick() {
		float step = 1.0F / Moons.FADE_TICKS;
		Moons.Moon target = Moons.clientMoon;
		if (target != Moons.Moon.NONE && (Moons.clientShown == target || Moons.clientShown == Moons.Moon.NONE)) {
			Moons.clientShown = target;
			Moons.clientFade = Math.min(1.0F, Moons.clientFade + step);
		} else if (Moons.clientFade > 0.0F) {
			// gone, or a different moon: the old one fades out first
			Moons.clientFade = Math.max(0.0F, Moons.clientFade - step);
		} else {
			Moons.clientShown = target;
		}
	}
}
