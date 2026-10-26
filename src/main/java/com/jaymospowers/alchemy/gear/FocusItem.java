package com.jaymospowers.alchemy.gear;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A focus: a small crystal that, set in the {@link RodItem Artificer's Rod}, gives it one {@link Focus} to cast. Does nothing alone. */
public class FocusItem extends Item {
	public final Focus focus;

	public FocusItem(Item.Properties properties, Focus focus) {
		super(properties);
		this.focus = focus;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.focus." + focus.id).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable("tooltip.jaymospowers.focus").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
