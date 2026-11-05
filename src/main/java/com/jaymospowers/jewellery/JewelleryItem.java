package com.jaymospowers.jewellery;

import com.jaymospowers.baubles.BaubleItem;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** (0.19.87) A gem ring, amulet, bracelet or circlet. Its bonus comes from its gem, grade, cut, metal and frame. */
public class JewelleryItem extends BaubleItem {
	public JewelleryItem(Item.Properties properties, JewelKind kind) {
		super(properties, kind.bauble, List.of());
	}

	@Override
	public List<Bonus> bonuses(ItemStack stack) {
		Bonus bonus = Jewellery.bonus(stack);
		return bonus == null ? List.of() : List.of(bonus);
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		super.appendHoverText(stack, context, display, builder, flag);
		CutGem cut = stack.get(Jewellery.CUT_GEM);
		if (cut != null && cut.gemType() != null) {
			Component grade = Component.translatable("grade.jaymospowers.cut." + cut.grade().id).withStyle(cut.grade().colour);
			builder.accept(Component.translatable("tooltip.jaymospowers.jewellery.set_with", cut.gemType().displayName(), grade)
				.withStyle(ChatFormatting.GRAY));
		}
	}
}
