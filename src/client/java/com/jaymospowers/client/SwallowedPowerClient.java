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
	private static net.minecraft.resources.@org.jspecify.annotations.Nullable Identifier lastSwallowed;

	private SwallowedPowerClient() {
	}

	static void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.swallowed", InputConstants.KEY_COMMA, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(SwallowedPowerClient::tick);
	}

	/** The key's name, for the HUD badge. */
	static net.minecraft.network.chat.Component keyName() {
		return key.getTranslatedKeyMessage();
	}

	private static void tick(Minecraft minecraft) {
		net.minecraft.client.player.LocalPlayer player = minecraft.player;
		if (player == null || minecraft.getConnection() == null) {
			wasDown = false;
			lastSwallowed = null;
			return;
		}
		// the swallowed power's client half runs as the server's does: its tick, its visuals, its R on this key
		com.jaymospowers.api.Power power = SwallowedPower.swallowed(player);
		net.minecraft.resources.Identifier id = power == null ? null : power.id();
		if (!java.util.Objects.equals(id, lastSwallowed)) {
			PowerClient old = PowerClients.get(lastSwallowed);
			if (old != null) {
				old.onActiveChanged(player, false);
			}
			PowerClient now = PowerClients.get(id);
			if (now != null) {
				now.onActiveChanged(player, true);
			}
			lastSwallowed = id;
		}
		PowerClient client = PowerClients.get(id);
		boolean down = key.isDown() && minecraft.gui.screen() == null;
		if (down != wasDown) {
			wasDown = down;
			ClientPlayNetworking.send(new SwallowedPower.Key(down));
			if (client != null) {
				client.onKey(player, com.jaymospowers.api.PowerKey.ABILITY_1, down);
			}
		}
		if (client != null) {
			client.tick(minecraft, player);
		}
	}
}
