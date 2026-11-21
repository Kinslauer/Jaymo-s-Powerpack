package com.jaymoskingdoms.client;

import com.jaymoskingdoms.AshRot;
import com.jaymoskingdoms.Patron;
import com.jaymoskingdoms.Patrons;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

/**
 * The Rot key (End, rebindable; with the boon key under Patrons), for the Ash Whisperer's sworn ({@link AshRot}): its press and
 * its release go to the server, which reads the hold and what you are doing (sneaking, sprinting, what you look at). Sent only while
 * sworn to the Ash and with no screen open.
 */
final class RotKey {
	private static KeyMapping key;
	private static boolean down;

	private RotKey() {
	}

	static void register(KeyMapping.Category category) {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.ash_rot", InputConstants.KEY_END, category));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			boolean now = key.isDown() && minecraft.player != null && minecraft.gui.screen() == null && Patrons.of(minecraft.player) == Patron.ASH_WHISPERER;
			if (now != down) {
				down = now;
				ClientPlayNetworking.send(new AshRot.Key(now));
			}
		});
	}
}
