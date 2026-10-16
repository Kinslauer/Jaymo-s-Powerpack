package com.jaymospowers.rig;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Rig Salvage (from the future's Dead Rigs) and Fallen Rig Scrap (from the Nether's and the End's Fallen Rigs, worth
 * less at the Forge: {@link RigForgeBlock#SALVAGE_POINTS}, {@link RigForgeBlock#SCRAP_POINTS}).
 */
public class SalvageItem extends Item {
	private final boolean fallen;

	public SalvageItem(boolean fallen, Item.Properties properties) {
		super(properties);
		this.fallen = fallen;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		String key = this.fallen ? "tooltip.jaymospowers.rig.scrap" : "tooltip.jaymospowers.rig.salvage";
		builder.accept(Component.translatable(key).withStyle(ChatFormatting.GRAY));
		builder.accept(Component.translatable(key + ".lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
