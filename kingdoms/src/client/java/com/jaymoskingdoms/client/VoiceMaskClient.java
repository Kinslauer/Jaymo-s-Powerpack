package com.jaymoskingdoms.client;

import com.jaymoskingdoms.VoiceMasks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/** (0.7.330) {@link VoiceMasks} arriving: handed to {@link VoiceChangerCompat} when Simple Voice Voice Changer is loaded. */
public final class VoiceMaskClient {
	private VoiceMaskClient() {
	}

	public static void register() {
		boolean loaded = FabricLoader.getInstance().isModLoaded("sv-voice-changer");
		ClientPlayNetworking.registerGlobalReceiver(VoiceMasks.Sync.TYPE, (payload, context) -> {
			if (loaded) {
				VoiceMasks.Mask[] masks = VoiceMasks.Mask.values();
				VoiceChangerCompat.apply(masks[Math.clamp(payload.mask(), 0, masks.length - 1)]);
			}
		});
		if (loaded) {
			ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> VoiceChangerCompat.apply(VoiceMasks.Mask.NONE));
		}
	}
}
