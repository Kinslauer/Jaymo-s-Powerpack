package com.jaymospowers.rig;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A Rig piece: armour, with what it does (and, on the Core, its charge and its Mind) in the tooltip. */
public class RigItem extends Item {
	final Rig.Piece piece;

	public RigItem(Rig.Piece piece, Item.Properties properties) {
		super(properties);
		this.piece = piece;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		String base = "tooltip.jaymospowers.rig." + this.piece.id();
		builder.accept(Component.translatable(base + ".passive").withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable(base + ".set").withStyle(ChatFormatting.DARK_AQUA));
		if (this.piece == Rig.Piece.CORE) {
			int charge = stack.getOrDefault(Rig.CHARGE, 0);
			builder.accept(Component.translatable("tooltip.jaymospowers.rig.charge", Math.round(charge * 100.0F / Rig.MAX_CHARGE))
				.withStyle(ChatFormatting.AQUA));
			String mind = stack.get(Rig.MIND);
			builder.accept(mind == null ? Component.translatable("tooltip.jaymospowers.rig.mind_asleep").withStyle(ChatFormatting.DARK_GRAY)
				: Component.translatable("tooltip.jaymospowers.rig.mind", Component.translatable("rig.jaymospowers.personality." + mind))
					.withStyle(ChatFormatting.AQUA));
		}
		if (this.piece == Rig.Piece.ARMS) {
			builder.accept(Component.translatable("tooltip.jaymospowers.rig.arms.slot").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
