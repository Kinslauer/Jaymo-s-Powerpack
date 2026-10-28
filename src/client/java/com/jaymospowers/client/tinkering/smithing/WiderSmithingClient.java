package com.jaymospowers.client.tinkering.smithing;

import com.jaymospowers.tinkering.smithing.Provenance;
import com.jaymospowers.tinkering.smithing.Wear;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** (Smithing) The maker's mark and the "Broken" line on tooltips. */
final class WiderSmithingClient {
	private WiderSmithingClient() {
	}

	static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (lines.isEmpty()) {
				return;
			}
			if (Wear.broken(stack)) {
				lines.add(1, Component.translatable("tooltip.jaymospowers.broken").withStyle(ChatFormatting.RED));
			}
			String maker = stack.get(Provenance.FORGED_BY);
			if (maker != null) {
				lines.add(Component.translatable("tooltip.jaymospowers.forged_by", maker).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		});
	}
}
