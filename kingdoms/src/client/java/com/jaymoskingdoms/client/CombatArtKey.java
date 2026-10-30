package com.jaymoskingdoms.client;

import com.jaymoskingdoms.CombatArts;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.forms.FormsClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

/**
 * (0.6.5) The Combat Arts' key ({@link CombatArts}): it charges; sneak and the key is the war cry. On mouse side button 1
 * since 0.7.255 (Y went to the Z Fighter technique wheel).
 */
final class CombatArtKey {
	private static KeyMapping key;

	private CombatArtKey() {
	}

	static void register() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.combat_art", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_4,
			KeyMapping.Category.register(JaymosKingdoms.id("combat_arts"))));
		// (0.7.371) with a form's weapon in hand the key is also the Battle Stance (hold) and the forms (tap): Weapon Forms decides
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> FormsClient.key(minecraft, key, () -> {
			if (minecraft.player != null && minecraft.gui.screen() == null && ClientPlayNetworking.canSend(CombatArts.Use.TYPE)) {
				ClientPlayNetworking.send(new CombatArts.Use(minecraft.player.isShiftKeyDown()));
			}
		}));
	}
}
