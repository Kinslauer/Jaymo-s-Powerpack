package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Pace;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

/** Walk slowly ({@link Pace}): hold the key, and the server is told while it is down and once more when it is let go. */
final class PaceKey {
	private static KeyMapping key;
	private static boolean sent;

	private PaceKey() {
	}

	static void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.slow_pace", InputConstants.KEY_CAPSLOCK,
			KeyMapping.Category.register(JaymosKingdoms.id("travel"))));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			boolean down = minecraft.player != null && minecraft.gui.screen() == null && key.isDown();
			if (down != sent && ClientPlayNetworking.canSend(Pace.Held.TYPE)) {
				sent = down;
				ClientPlayNetworking.send(new Pace.Held(down));
			}
		});
	}
}
