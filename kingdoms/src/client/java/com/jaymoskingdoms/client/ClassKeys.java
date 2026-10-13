package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Spellcraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

/** (0.6.0) The classes' key: {@code J} imbues a Sorcerer's blade ({@link Spellcraft}); sneak and {@code J} throws the bolt. */
final class ClassKeys {
	private static KeyMapping imbue;

	private ClassKeys() {
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("classes"));
		imbue = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.imbue", InputConstants.KEY_J, category));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (imbue.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null) {
					ClientPlayNetworking.send(new Spellcraft.Imbue(minecraft.player.isShiftKeyDown()));
				}
			}
		});
	}
}
