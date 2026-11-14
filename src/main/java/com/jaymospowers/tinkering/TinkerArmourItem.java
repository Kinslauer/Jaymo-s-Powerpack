package com.jaymospowers.tinkering;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * A piece of Tinkers' armour put together at the Tool Station from a plating and a maille: plate armour, or the travelers'
 * piece when the maille is leather. Its armour, look and wear all come from its parts ({@link ToolStats#armour}); its
 * metals' armour traits work in {@link ArmourTraits}.
 */
public class TinkerArmourItem extends Item implements TinkerTool {
	public final ToolType type;

	public TinkerArmourItem(Item.Properties properties, ToolType type) {
		super(properties);
		this.type = type;
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		TinkerTool.tick(stack, level);
	}

	@Override
	public ToolType type() {
		return type;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		TinkerTool.tooltip(stack, type, builder);
	}
}
