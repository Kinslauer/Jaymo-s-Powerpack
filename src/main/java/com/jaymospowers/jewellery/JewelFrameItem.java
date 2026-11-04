package com.jaymospowers.jewellery;

import com.jaymospowers.tinkering.smeltery.Molten;
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

/** (0.19.87) A forged ring, bracelet, amulet or circlet frame: metal with an empty setting for a cut gem. */
public class JewelFrameItem extends Item {
	public JewelFrameItem(Item.Properties properties) {
		super(properties);
	}

	/** A frame of this kind in this metal (cold and ungraded; the anvil grades it). */
	public static ItemStack of(JewelKind kind, Molten metal) {
		ItemStack stack = new ItemStack(Jewellery.FRAMES.get(kind));
		stack.set(Jewellery.JEWEL_METAL, metal.id);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(metal.id), List.of()));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymospowers." + kind.frameId + ".named", Jewellery.metalName(metal.id)));
		return stack;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.jewellery.frame_hint").withStyle(ChatFormatting.DARK_GRAY));
	}
}
