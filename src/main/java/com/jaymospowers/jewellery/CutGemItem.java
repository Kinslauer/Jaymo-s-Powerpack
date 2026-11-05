package com.jaymospowers.jewellery;

import com.jaymospowers.tinkering.smithing.Grade;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;

/** (0.19.87) A gem cut on the lapidary wheel. Its gem, cut, grade and chips are one component ({@link CutGem}). */
public class CutGemItem extends Item {
	public CutGemItem(Item.Properties properties) {
		super(properties);
	}

	/** A cut gem of this gem, cut and grade. */
	public static ItemStack of(Gems.Gem gem, Cut cut, Grade quality, int chips) {
		ItemStack stack = new ItemStack(Jewellery.CUT_GEM_ITEM);
		stack.set(Jewellery.CUT_GEM, new CutGem(gem.id(), cut.id, quality.id, chips));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(cut.id), List.of(gem.colour())));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymospowers.cut_gem.named", cut.displayName(), gem.displayName()));
		if (quality == Grade.MASTER) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return stack;
	}

	/** (0.20.7) The cut gem item for a saved {@link CutGem} (a gem pulled from a socket), or EMPTY for an unknown gem. */
	public static ItemStack of(CutGem cut) {
		Gems.Gem gem = cut.gemType();
		return gem == null ? ItemStack.EMPTY : of(gem, cut.cutType(), cut.grade(), cut.chips());
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		CutGem cut = stack.get(Jewellery.CUT_GEM);
		if (cut == null) {
			return;
		}
		Grade grade = cut.grade();
		builder.accept(Component.translatable("grade.jaymospowers.cut." + grade.id).withStyle(grade.colour));
		if (cut.chips() > 0) {
			builder.accept(Component.translatable("tooltip.jaymospowers.jewellery.chipped", cut.chips()).withStyle(ChatFormatting.RED));
		}
		Gems.Gem gem = cut.gemType();
		if (gem != null) {
			builder.accept(Component.translatable("tooltip.jaymospowers.jewellery.gives",
				Component.translatable(gem.attribute().value().getDescriptionId())).withStyle(ChatFormatting.BLUE));
		}
		builder.accept(Component.translatable("tooltip.jaymospowers.jewellery.gem_hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
