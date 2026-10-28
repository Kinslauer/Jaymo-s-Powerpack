package com.jaymospowers.tinkering.smithing;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * (Smithing) Metal part-way through the hammering ({@link Smithing#FORGE_WORK}). It goes cold like any hot metal: take
 * it back to the hearth, then lay it on the anvil again to carry on where you left off.
 */
public class ForgingBlankItem extends Item {
	public ForgingBlankItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		Smithing.Work work = stack.get(Smithing.FORGE_WORK);
		Forging.Option option = work == null ? null : Forging.byId(work.option());
		if (option == null) {
			return;
		}
		builder.accept(Component.translatable("tooltip.jaymospowers.forging_blank.becoming", option.result().getHoverName())
			.withStyle(ChatFormatting.GOLD));
		builder.accept(Component.translatable("tooltip.jaymospowers.forging_blank.blows", work.hits(), option.hits())
			.withStyle(ChatFormatting.GRAY));
	}

	/** What the anvil is making of it, or null. */
	public static Forging.Option option(ItemStack blank) {
		Smithing.Work work = blank.get(Smithing.FORGE_WORK);
		return work == null ? null : Forging.byId(work.option());
	}
}
