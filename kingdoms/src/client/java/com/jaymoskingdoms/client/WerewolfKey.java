package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.WerewolfPlayers;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

/** (0.7.0) A werewolf player's change at will ({@link WerewolfPlayers}): {@code U}. */
final class WerewolfKey {
	private static KeyMapping key;

	private WerewolfKey() {
	}

	static void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.werewolf_shift", InputConstants.KEY_U,
			KeyMapping.Category.register(JaymosKingdoms.id("werewolf"))));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (key.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null && ClientPlayNetworking.canSend(WerewolfPlayers.Shift.TYPE)) {
					ClientPlayNetworking.send(new WerewolfPlayers.Shift());
				}
			}
		});
	}
}
