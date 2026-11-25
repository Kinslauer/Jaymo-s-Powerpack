package com.jaymoskingdoms.client;

import com.jaymoskingdoms.TownDoings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;

/** (Village mods patch 5) What the town is doing ({@link TownDoings}), opened on Minecraft's own book page. */
public final class TownDoingsClient {
	private TownDoingsClient() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(TownDoings.Page.TYPE, (payload, context) ->
			context.client().gui.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(payload.pages()))));
	}
}
