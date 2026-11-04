package com.jaymoskingdoms.client;

import com.jaymoskingdoms.LoreCodex;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** (0.7.490) Hordes, Orc war journals: opens the {@link LoreCodexScreen} when the server sends the codex to read. */
public final class LoreCodexClient {
	private LoreCodexClient() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LoreCodex.Open.TYPE, (payload, context) -> context.client().gui.setScreen(new LoreCodexScreen(payload)));
	}
}
