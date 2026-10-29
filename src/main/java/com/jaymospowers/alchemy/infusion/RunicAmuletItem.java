package com.jaymospowers.alchemy.infusion;

import com.jaymospowers.alchemy.Corruption;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The runic amulet: a gold trinket from the altar. While it is carried, anywhere in the inventory, or worn in the amulet bauble slot, it slowly draws the
 * fleeting corruption out of its bearer ({@link #BLEED_TICKS} per point). It does nothing for lingering or deep corruption.
 */
public class RunicAmuletItem extends Item {
	/** Ticks between the amulet taking one point of fleeting corruption (15 s). */
	public static final int BLEED_TICKS = 300;

	public RunicAmuletItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
		if (entity instanceof ServerPlayer player && level.getGameTime() % BLEED_TICKS == 0 && Corruption.of(player).fleeting() > 0) {
			Corruption.add(player, Corruption.Kind.FLEETING, -1);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.runic_amulet").withStyle(ChatFormatting.GRAY));
	}
}
