package com.jaymospowers.tinkering.smithing;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * (Smithing) Smithing tongs (wooden, iron, steel; after Overgeared's): in either hand they let you carry hot metal
 * without catching fire, wearing a point every {@link HotMetal#TONGS_WEAR_TICKS} instead.
 */
public class TongsItem extends Item {
	public TongsItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.tongs").withStyle(ChatFormatting.GRAY));
	}
}
