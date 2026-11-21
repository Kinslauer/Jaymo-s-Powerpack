package com.jaymospowers.client.bonebond;

import com.jaymospowers.bonebond.Boneiron;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** (Awakening pick) A plated piece of armour says how many boneiron platings it carries ({@link Boneiron}). */
public final class BoneironClient {
	private BoneironClient() {
	}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			int plated = stack.getOrDefault(Boneiron.PLATED, 0);
			if (plated > 0) {
				lines.add(Component.translatable("tooltip.jaymospowers.boneiron_plated", plated, Boneiron.PLATINGS).withStyle(ChatFormatting.GRAY));
			}
		});
	}
}
