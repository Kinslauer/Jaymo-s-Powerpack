package com.jaymospowers.gear.arrow;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A loose arrowhead: fit it to plain arrows at the Fletching Station (put the arrows and the head in, take the fitted arrows out). */
public class ArrowHeadItem extends Item {
	public final ArrowHead head;

	public ArrowHeadItem(Item.Properties properties, ArrowHead head) {
		super(properties);
		this.head = head;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.head." + this.head.id).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.jaymospowers.head.fit", Fitting.ARROWS_PER_HEAD).withStyle(ChatFormatting.DARK_GRAY));
	}
}
