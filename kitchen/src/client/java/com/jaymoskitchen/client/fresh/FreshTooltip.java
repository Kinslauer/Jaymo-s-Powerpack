package com.jaymoskitchen.client.fresh;

import com.jaymoskitchen.fresh.Freshness;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Freshness on the tooltip (fresh, going stale, nearly off; preserved), and the client's day for the freshness bar. */
public final class FreshTooltip {
	private FreshTooltip() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> Freshness.clientToday = client.level == null ? -1L : Freshness.today(client.level));
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (Freshness.clientToday < 0 || !Freshness.perishable(stack)) {
				return;
			}
			float left = Freshness.left(stack, Freshness.clientToday);
			if (left >= 0.0F) {
				String key = left > 0.6F ? "fresh" : left > 0.25F ? "stale" : "off";
				ChatFormatting colour = left > 0.6F ? ChatFormatting.GREEN : left > 0.25F ? ChatFormatting.YELLOW : ChatFormatting.RED;
				lines.add(Component.translatable("tooltip.jaymoskitchen.fresh." + key).withStyle(colour));
			}
			if (stack.has(Freshness.PRESERVED)) {
				lines.add(Component.translatable("tooltip.jaymoskitchen.fresh.preserved").withStyle(ChatFormatting.GOLD));
			}
		});
	}
}
