package com.jaymoskingdoms.client;

import com.jaymoskingdoms.StoryHard;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Cast Out road wakes you with an empty map (the NewRoads plan; Andy, 2026-11-12: "fix it"): while your own kind are
 * still turning you away ({@link StoryHard#castOutActive}, the first days), Xaero's Minimap shows nothing, no waypoint is
 * drawn in the world or on the map, and Xaero's World Map won't open. You learn the land again. Nothing without Xaero.
 */
public final class CastOutMap {
	private CastOutMap() {
	}

	/** Whether the map is empty for you just now. */
	public static boolean empty() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && StoryHard.castOutActive(mc.player);
	}

	static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Screen open = client.gui.screen();
			if (open != null && open.getClass().getName().startsWith("xaero.map.gui.") && empty()) {
				client.gui.setScreen(null);
				if (client.player != null) {
					client.player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hard.cast.no_map").withStyle(ChatFormatting.GRAY));
				}
			}
		});
	}
}
