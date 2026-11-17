package com.jaymospowers.bonebond;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.Equippable;

/** A boneiron plate ({@link Boneiron}): click it onto a piece of armour to plate it, up to {@link Boneiron#PLATINGS} a piece. */
public class BoneironPlateItem extends Item {
	public BoneironPlateItem(Properties properties) {
		super(properties);
	}

	/** Whether {@code stack} is armour worn on the head, body, legs or feet. */
	static boolean armour(ItemStack stack) {
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
	}

	@Override
	public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction action, Player player) {
		ItemStack armour = slot.getItem();
		if (action != ClickAction.PRIMARY || !armour(armour) || armour.getCount() != 1 || !slot.allowModification(player)) {
			return false;
		}
		int had = armour.getOrDefault(Boneiron.PLATED, 0);
		if (had >= Boneiron.PLATINGS) {
			player.playSound(SoundEvents.CHAIN_HIT, 0.6F, 0.6F);
			return true;
		}
		armour.set(Boneiron.PLATED, had + 1);
		slot.setChanged();
		self.shrink(1);
		player.playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8F, 0.8F);
		return true;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.boneiron_plate", Boneiron.PLATINGS).withStyle(ChatFormatting.GRAY));
	}
}
