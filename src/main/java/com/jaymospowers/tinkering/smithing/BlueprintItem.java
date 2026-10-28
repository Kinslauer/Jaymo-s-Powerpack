package com.jaymospowers.tinkering.smithing;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** (Smithing) A blueprint ({@link Blueprints}): carry it in your inventory while you hammer its shape. */
public class BlueprintItem extends Item {
	public BlueprintItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		Blueprints.Blueprint data = stack.get(Blueprints.BLUEPRINT);
		if (data == null) {
			builder.accept(Component.translatable("tooltip.jaymospowers.smithing.blueprint.blank").withStyle(ChatFormatting.GRAY));
			return;
		}
		Grade level = data.level();
		builder.accept(Component.translatable("tooltip.jaymospowers.smithing.blueprint.shape", Blueprints.shapeName(data.shape()))
			.withStyle(ChatFormatting.GOLD));
		builder.accept(Component.translatable("tooltip.jaymospowers.smithing.blueprint.level", level.displayName()).withStyle(ChatFormatting.GRAY));
		if (level != Grade.MASTER) {
			Grade next = level.higher();
			builder.accept(Component.translatable("tooltip.jaymospowers.smithing.blueprint.next", data.xp(), Blueprints.xpOf(next), next.displayName())
				.withStyle(ChatFormatting.DARK_GRAY));
		}
		builder.accept(Component.translatable("tooltip.jaymospowers.smithing.blueprint.use").withStyle(ChatFormatting.DARK_GRAY));
	}
}
