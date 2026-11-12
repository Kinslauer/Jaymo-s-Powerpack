package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.QuickOrders;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

/**
 * The four quick orders' keys ({@link QuickOrders}), rebindable, in the Followers group beside the orders screen's: Go on
 * the Up arrow, Help on the Left, Wait on the Right, Come on Right Shift (the Down arrow is the crawl's). A press sends
 * the order; there's no screen.
 */
public final class QuickOrderKeys {
	private static final KeyMapping[] KEYS = new KeyMapping[4];

	private QuickOrderKeys() {
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("quick_orders"));
		int[] defaults = {InputConstants.KEY_UP, InputConstants.KEY_RSHIFT, InputConstants.KEY_RIGHT, InputConstants.KEY_LEFT};
		for (int kind = QuickOrders.GO; kind <= QuickOrders.HELP; kind++) {
			KEYS[kind] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.quick_" + new String[] {"go", "come", "wait", "help"}[kind],
				defaults[kind], category));
		}
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			for (int kind = QuickOrders.GO; kind <= QuickOrders.HELP; kind++) {
				while (KEYS[kind].consumeClick()) {
					if (minecraft.player != null && minecraft.gui.screen() == null && ClientPlayNetworking.canSend(QuickOrders.Give.TYPE)) {
						ClientPlayNetworking.send(new QuickOrders.Give(kind));
					}
				}
			}
		});
	}
}
