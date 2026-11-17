package com.jaymoskingdoms.client;

import com.jaymoskingdoms.ArtKeys;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.forms.FormsClient;
import com.jaymoskingdoms.forms.Forms;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

/**
 * The four art keys ({@link ArtKeys}; the arrow keys up, left, down and right, on every keyboard, rebindable, under their own
 * heading): Soul, Claw, Wild and Sun. In combat mode ({@link FormsClient#combatMode()}) the number keys after the belt's, 6 to 9,
 * are the four art keys too, and pick no hotbar slot. Each key's
 * press and release go to the server, which reads the gesture (as the Rot key's is read). Sent only with no screen open; the
 * server ignores a key whose arts are not the player's.
 */
final class ArtKeysClient {
	private static final String[] NAMES = {"art_soul", "art_claw", "art_wild", "art_sun"};
	private static final int[] DEFAULTS = {InputConstants.KEY_UP, InputConstants.KEY_LEFT, InputConstants.KEY_DOWN, InputConstants.KEY_RIGHT};
	private static final KeyMapping[] KEYS = new KeyMapping[ArtKeys.ARTS];
	private static final boolean[] DOWN = new boolean[ArtKeys.ARTS];

	private ArtKeysClient() {
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("arts"));
		for (int i = 0; i < ArtKeys.ARTS; i++) {
			KEYS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms." + NAMES[i], DEFAULTS[i], category));
		}
		ClientTickEvents.START_CLIENT_TICK.register(minecraft -> {
			// combat mode: 6 to 9 are art keys, so they pick no hotbar slot
			if (FormsClient.combatMode() && minecraft.player != null && minecraft.gui.screen() == null) {
				for (int i = 0; i < ArtKeys.ARTS; i++) {
					while (minecraft.options.keyHotbarSlots[Forms.BELT_SLOTS + i].consumeClick()) {
						// taken
					}
				}
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			for (int i = 0; i < ArtKeys.ARTS; i++) {
				boolean now = (KEYS[i].isDown() || FormsClient.combatMode() && minecraft.options.keyHotbarSlots[Forms.BELT_SLOTS + i].isDown())
					&& minecraft.player != null && minecraft.gui.screen() == null;
				if (now != DOWN[i]) {
					DOWN[i] = now;
					if (minecraft.player != null) {
						ClientPlayNetworking.send(new ArtKeys.Key(i, now));
					}
				}
			}
		});
	}
}
