package com.jaymoskingdoms.client;

import com.jaymoskingdoms.FrostPrison;
import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

/** (0.7.75) The heritage key, {@code ;}: a Warrior-born's tail, wrapped away or let out ({@link Heritage#gift}). */
final class FrostPrisonKey {
	private static KeyMapping key;

	private FrostPrisonKey() {
	}

	static void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.frost_prison", InputConstants.KEY_SEMICOLON,
			KeyMapping.Category.register(JaymosKingdoms.id("heritage"))));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (key.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null && Heritage.is(minecraft.player, Heritage.Kind.WARRIORBORN)
					&& ClientPlayNetworking.canSend(FrostPrison.Cast.TYPE)) {
					ClientPlayNetworking.send(new FrostPrison.Cast());
				}
			}
		});
	}
}
