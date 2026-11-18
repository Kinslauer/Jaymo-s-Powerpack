package com.jaymospowers.bonebond;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.Equippable;

/**
 * A boneiron plate ({@link Boneiron}): laid in a Tool Forge with a piece of armour it plates the piece, up to
 * {@link Boneiron#PLATINGS} a piece ({@link com.jaymospowers.tinkering.ToolStationMenu}).
 */
public class BoneironPlateItem extends Item {
	public BoneironPlateItem(Properties properties) {
		super(properties);
	}

	/** Whether {@code stack} is armour worn on the head, body, legs or feet. */
	public static boolean armour(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.boneiron_plate", Boneiron.PLATINGS).withStyle(ChatFormatting.GRAY));
	}
}
