package com.jaymospowers.client.combat;

import com.jaymospowers.combat.Blows;
import com.jaymospowers.combat.WeaponKind;
import com.jaymospowers.core.ArmourKind;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * What a weapon's blow is and where it wants to be ({@link WeaponKind}), and what a piece of armour is made like
 * ({@link ArmourKind}), as one grey line on the item ({@link Blows} is what they decide).
 */
public final class BlowTooltips {
	private BlowTooltips() {
	}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			ArmourKind armour = ArmourKind.of(stack);
			if (armour != ArmourKind.NONE) {
				lines.add(Component.translatable(armour.key()).withStyle(ChatFormatting.GRAY));
				return;
			}
			WeaponKind kind = WeaponKind.of(stack);
			if (kind != null) {
				lines.add(Component.translatable(kind.key()).withStyle(ChatFormatting.GRAY));
			}
		});
	}
}
