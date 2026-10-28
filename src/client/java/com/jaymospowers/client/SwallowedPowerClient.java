package com.jaymospowers.client;

import com.jaymospowers.core.SwallowedPower;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * (0.17.108) The Swallowed Power key (comma by default): the R of a power a Rubber-folk swallowed ({@link SwallowedPower}),
 * held and let go like R. Sent only on a change; the server ignores it for anyone who swallowed nothing.
 */
final class SwallowedPowerClient {
	private static KeyMapping key;
	private static boolean wasDown;

	private SwallowedPowerClient() {
	}

	static void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.swallowed", InputConstants.KEY_COMMA, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(SwallowedPowerClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.getConnection() == null) {
			wasDown = false;
			return;
		}
		boolean down = key.isDown() && minecraft.gui.screen() == null;
		if (down != wasDown) {
			wasDown = down;
			ClientPlayNetworking.send(new SwallowedPower.Key(down));
		}
	}
}
