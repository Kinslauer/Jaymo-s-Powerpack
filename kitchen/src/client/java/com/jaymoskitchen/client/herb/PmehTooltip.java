package com.jaymoskitchen.client.herb;

import com.jaymoskitchen.herb.PmehFarming;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** (0.5.29) Pmeh and fresh Pmeh show their grade, the thing dealers look at. */
public final class PmehTooltip {
	private static final ChatFormatting[] COLOURS = {ChatFormatting.DARK_GRAY, ChatFormatting.GRAY, ChatFormatting.GREEN, ChatFormatting.LIGHT_PURPLE};

	private PmehTooltip() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (PmehFarming.isPmeh(stack)) {
				int grade = PmehFarming.grade(stack);
				lines.add(Component.translatable("item.jaymoskitchen.pmeh.grade", Component.translatable(PmehFarming.gradeKey(grade)))
					.withStyle(COLOURS[grade]));
			}
		});
	}
}
