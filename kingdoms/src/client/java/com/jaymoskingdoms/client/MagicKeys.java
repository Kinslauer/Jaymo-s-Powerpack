package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Cantrips;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.MagicMenu;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/**
 * The Magic key (default Page Up): answers a spell being cast in sight if the player knows counterspell, otherwise asks the
 * server to open the Magic screen (the everyday spells and the rituals). The server decides who may; the replies open the
 * screens. Also marks a warmed or chilled dish in its tooltip.
 */
public final class MagicKeys {
	private static KeyMapping magic;

	private MagicKeys() {
	}

	public static void register() {
		magic = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.magic", InputConstants.KEY_PAGEUP,
			KeyMapping.Category.register(JaymosKingdoms.id("classes"))));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (magic.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null) {
					ClientPlayNetworking.send(new MagicMenu.Pick("key", ""));
				}
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(MagicMenu.Open.TYPE, (payload, context) ->
			context.client().execute(() -> context.client().gui.setScreen(new MagicScreen(payload.cantrips(), payload.rituals(), payload.choices()))));
		ClientPlayNetworking.registerGlobalReceiver(MagicMenu.Page.TYPE, (payload, context) ->
			context.client().execute(() -> context.client().gui.setScreen(new MagicPageScreen(payload.title(), payload.lines()))));
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			String temp = Cantrips.dishTemp(stack);
			if (temp != null) {
				lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.jaymoskingdoms.dish." + temp).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		});
	}
}
