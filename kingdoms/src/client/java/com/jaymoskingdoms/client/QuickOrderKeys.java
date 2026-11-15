package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Formations;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.QuickOrders;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

/**
 * The four quick orders' keys ({@link QuickOrders}), rebindable, in the Followers group beside the orders screen's: Go on
 * the Up arrow, Come on the Down arrow, Help on the Left, Wait on the Right. A press sends
 * the order; there's no screen.
 */
public final class QuickOrderKeys {
	private static final KeyMapping[] KEYS = new KeyMapping[4];
	/** (medieval picks) The formation's keys ({@link Formations}): the next shape (F6), go where you look or at whom (F8), charge (F9). */
	private static final KeyMapping[] FORMATION_KEYS = new KeyMapping[3];
	private static final int[] FORMATION_ORDERS = {Formations.CYCLE, Formations.POINT, Formations.CHARGE};

	private QuickOrderKeys() {
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("quick_orders"));
		int[] defaults = {InputConstants.KEY_UP, InputConstants.KEY_DOWN, InputConstants.KEY_RIGHT, InputConstants.KEY_LEFT};
		for (int kind = QuickOrders.GO; kind <= QuickOrders.HELP; kind++) {
			KEYS[kind] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.quick_" + new String[] {"go", "come", "wait", "help"}[kind],
				defaults[kind], category));
		}
		// (medieval picks) the formation's three: the next shape, go there or at them (where you look), charge
		for (int i = 0; i < FORMATION_KEYS.length; i++) {
			FORMATION_KEYS[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.formation_" + new String[] {"shape", "point", "charge"}[i],
				new int[] {InputConstants.KEY_F6, InputConstants.KEY_F8, InputConstants.KEY_F9}[i], category));
		}
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			for (int kind = QuickOrders.GO; kind <= QuickOrders.HELP; kind++) {
				while (KEYS[kind].consumeClick()) {
					if (minecraft.player != null && minecraft.gui.screen() == null && ClientPlayNetworking.canSend(QuickOrders.Give.TYPE)) {
						ClientPlayNetworking.send(new QuickOrders.Give(kind));
					}
				}
			}
			for (int i = 0; i < FORMATION_KEYS.length; i++) {
				while (FORMATION_KEYS[i].consumeClick()) {
					if (minecraft.player != null && minecraft.gui.screen() == null && ClientPlayNetworking.canSend(Formations.Give.TYPE)) {
						ClientPlayNetworking.send(new Formations.Give(FORMATION_ORDERS[i], -1));
					}
				}
			}
		});
	}
}
