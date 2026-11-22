package com.jaymoskitchen.client.vintner;

import com.jaymoskitchen.vintner.Vintage;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** A bottle of wine shows the year it was made and how many years it has improved in a cellar. */
public final class VintageTooltip {
	private VintageTooltip() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			int year = Vintage.year(stack);
			if (year < 0) {
				return;
			}
			int age = Vintage.age(stack);
			lines.add(Component.translatable(age > 0 ? "tooltip.jaymoskitchen.vintage.aged" : "tooltip.jaymoskitchen.vintage", year + 1, age)
				.withStyle(ChatFormatting.DARK_RED));
		});
	}
}
