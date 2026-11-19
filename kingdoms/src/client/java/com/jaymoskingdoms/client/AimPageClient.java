package com.jaymoskingdoms.client;

import com.jaymoskingdoms.LifeAims;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;

/**
 * The life aim's page ({@link LifeAims.Page}), opened on Minecraft's own book page as the player lands, in place of the
 * one-page book that used to go in their pack. It waits until no screen has been open for {@link #CLEAR_TICKS}, so the
 * landing's loading screen (StoryHoldScreen) and the creator are gone and nothing opens straight over it.
 */
public final class AimPageClient {
	/** Ticks with no screen open before the page shows. */
	static final int CLEAR_TICKS = 20;

	private static String pending;
	private static int clear;

	private AimPageClient() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LifeAims.Page.TYPE, (payload, context) -> {
			pending = payload.key();
			clear = 0;
		});
		ClientTickEvents.END_CLIENT_TICK.register(AimPageClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		if (pending == null) {
			return;
		}
		if (minecraft.player == null) {
			pending = null;
			return;
		}
		if (minecraft.gui.screen() != null) {
			clear = 0;
			return;
		}
		if (++clear < CLEAR_TICKS) {
			return;
		}
		minecraft.gui.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(List.of(Component.translatable(pending)))));
		pending = null;
	}
}
