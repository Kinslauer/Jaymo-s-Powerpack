package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Spellcraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** (0.6.0) The classes' key: {@code J} imbues a Sorcerer's blade ({@link Spellcraft}); sneak and {@code J} throws the bolt. */
final class ClassKeys {
	private static KeyMapping imbue;

	private ClassKeys() {
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("classes"));
		imbue = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.imbue", InputConstants.KEY_J, category));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (imbue.consumeClick()) {
				if (minecraft.player != null && minecraft.gui.screen() == null) {
					ClientPlayNetworking.send(new Spellcraft.Imbue(minecraft.player.isShiftKeyDown()));
				}
			}
		});
		// (0.7.49) an imbued blade says whose element it carries and for how long, in anyone's hands
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Spellcraft.Charge charge = Spellcraft.charge(stack);
			Minecraft minecraft = Minecraft.getInstance();
			if (charge == null || minecraft.level == null) {
				return;
			}
			long left = charge.until() - minecraft.level.getGameTime();
			if (left <= 0) {
				return;
			}
			lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.jaymoskingdoms.imbued",
				Component.translatable("class.jaymoskingdoms.option." + charge.element()), charge.name(), left / 20)
				.withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
		});
	}
}
