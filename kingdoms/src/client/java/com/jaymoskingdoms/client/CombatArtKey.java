package com.jaymoskingdoms.client;

import com.jaymoskingdoms.CombatArts;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

/** (0.6.5) The Combat Arts' key ({@link CombatArts}): {@code Y} charges; sneak and {@code Y} is the war cry. */
final class CombatArtKey {
	private static KeyMapping key;

	private CombatArtKey() {
	}

	static void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.combat_art", InputConstants.KEY_Y,
			KeyMapping.Category.register(JaymosKingdoms.id("combat_arts"))));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (key.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null && ClientPlayNetworking.canSend(CombatArts.Use.TYPE)) {
					ClientPlayNetworking.send(new CombatArts.Use(minecraft.player.isShiftKeyDown()));
				}
			}
		});
	}
}
