package com.jaymospowers.client.combat;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.combat.KatanaForms;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

public final class KatanaFormsClient {
	private KatanaFormsClient() {
	}

	public static void init() {
		KeyMapping key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.jaymospowers.kata", InputConstants.UNKNOWN.getValue(), JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (key.consumeClick()) {
				if (minecraft.gui.screen() == null && minecraft.player != null && KatanaForms.ready(minecraft.player)
					&& ClientPlayNetworking.canSend(KatanaForms.Key.TYPE)) {
					ClientPlayNetworking.send(new KatanaForms.Key(true));
					held = true;
				}
			}
			if (held && (!key.isDown() || minecraft.gui.screen() != null || minecraft.player == null)) {
				if (minecraft.player != null && ClientPlayNetworking.canSend(KatanaForms.Key.TYPE)) {
					ClientPlayNetworking.send(new KatanaForms.Key(false));
				}
				held = false;
			}
		});
	}

	private static boolean held;
}
