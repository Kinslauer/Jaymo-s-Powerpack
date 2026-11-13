package com.jaymospowers.client.baubles;

import com.jaymospowers.baubles.Bonds;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The tooltip lines for bonding: whether a strong thing is bonded to you, a curse once it has shown, an unknown nature. */
public final class BondsClient {
	private BondsClient() {
	}

	public static void init() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!Bonds.needs(stack)) {
				return;
			}
			Player player = Minecraft.getInstance().player;
			if (player == null) {
				return;
			}
			if (Bonds.bonded(player, stack)) {
				lines.add(Component.translatable("tooltip.jaymospowers.bonded", Bonds.bonds(player).size(), Bonds.MOST).withStyle(ChatFormatting.LIGHT_PURPLE));
			} else {
				lines.add(Component.translatable("tooltip.jaymospowers.unbonded").withStyle(ChatFormatting.DARK_GRAY));
			}
			addCurse(stack, lines);
		});
	}

	private static void addCurse(ItemStack stack, java.util.List<Component> lines) {
		String curse = Bonds.curse(stack);
		if (curse != null && Bonds.curseShown(stack) && (Bonds.has(stack, Bonds.BOND) || Bonds.has(stack, Bonds.KNOWN))) {
			lines.add(Component.translatable("tooltip.jaymospowers.curse." + curse).withStyle(ChatFormatting.DARK_RED));
		}
	}
}
