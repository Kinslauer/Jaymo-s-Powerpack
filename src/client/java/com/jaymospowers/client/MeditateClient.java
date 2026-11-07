package com.jaymospowers.client;

import com.jaymospowers.core.Meditate;
import com.jaymospowers.core.Sitting;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * The Meditate key (I by default; shown as Shadowbox since Motion patch 0): held, it tells the server; let go (or a screen
 * opens), it tells it again. (0.19.67) The Sit key (] by default): each press tells the server (see {@link Sitting}).
 */
final class MeditateClient {
	private static KeyMapping key;
	private static KeyMapping sit;
	private static boolean wasDown;

	private MeditateClient() {
	}

	static void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.meditate", InputConstants.KEY_I, JaymosPowersClient.CATEGORY));
		sit = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.sit", InputConstants.KEY_RBRACKET, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(MeditateClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (minecraft.player == null || minecraft.getConnection() == null) {
			wasDown = false;
			while (sit.consumeClick()) {
				// nothing to sit in
			}
			return;
		}
		while (sit.consumeClick()) {
			if (minecraft.gui.screen() == null) {
				ClientPlayNetworking.send(new Sitting.Key());
			}
		}
		boolean down = key.isDown() && minecraft.gui.screen() == null;
		if (down != wasDown) {
			wasDown = down;
			ClientPlayNetworking.send(new Meditate.Key(down));
		}
	}
}
