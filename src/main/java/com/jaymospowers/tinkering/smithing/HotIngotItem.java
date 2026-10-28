package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.smeltery.Molten;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/** (Smithing) An ingot glowing from the hearth: lay it on a Smithing Anvil. It cools back to its ingot ({@link HotMetal}). */
public class HotIngotItem extends Item {
	public HotIngotItem(Item.Properties properties) {
		super(properties);
	}

	/** Its model (one per metal) and name ("Hot Steel Ingot"). */
	public static void look(ItemStack stack, Molten molten) {
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(molten.id), List.of()));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymospowers.hot_ingot.named", molten.displayName()));
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		if (!stack.has(DataComponents.CUSTOM_MODEL_DATA)) {
			Molten molten = HotMetal.metal(stack);
			look(stack, molten == null ? Molten.IRON : molten);
		}
		if (!stack.has(Smithing.HEATED_AT)) {
			stack.set(Smithing.HEATED_AT, level.getGameTime());
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.hot_ingot").withStyle(ChatFormatting.GRAY));
	}
}
