package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Journal;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * (0.5.49) The journal key (M): with a traveller's journal in your inventory it asks the server to write the journal up
 * ({@link Journal}) and opens it ({@link JournalScreen}); without one it says how to make one.
 */
public final class JournalClient {
	private static KeyMapping journalKey;

	private JournalClient() {
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("journal"));
		journalKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.journal", InputConstants.KEY_M, category));
		ClientPlayNetworking.registerGlobalReceiver(Journal.Book.TYPE, (payload, context) -> context.client().gui.setScreen(new JournalScreen(payload, journalKey)));
		ClientTickEvents.END_CLIENT_TICK.register(JournalClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		while (journalKey.consumeClick()) {
			if (minecraft.player == null) {
				continue;
			}
			if (Journal.carries(minecraft.player)) {
				ClientPlayNetworking.send(new Journal.Ask());
			} else {
				minecraft.player.sendOverlayMessage(Component.translatable("journal.jaymoskingdoms.need").withStyle(ChatFormatting.GRAY));
			}
		}
	}
}
