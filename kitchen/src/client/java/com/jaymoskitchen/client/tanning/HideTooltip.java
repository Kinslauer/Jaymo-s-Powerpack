package com.jaymoskitchen.client.tanning;

import com.jaymoskitchen.tanning.HideOrigin;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

/**
 * A hide, a piece of leather or leather armour names the animal it came off ("Cow hide"), and nothing more: its trait,
 * if it has one, is never written (Andy: "dont list them anywhere in game").
 */
public final class HideTooltip {
	private HideTooltip() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (HideOrigin.HIDE_OF == null) {
				return;
			}
			Identifier animal = stack.get(HideOrigin.HIDE_OF);
			if (animal != null) {
				lines.add(Math.min(1, lines.size()), HideOrigin.leatherName(animal).withStyle(ChatFormatting.GRAY));
			}
		});
	}
}
