package com.jaymospowers.layers;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * (0.17.116) The saw: set in a crafting grid with a wooden block, it cuts the block into {@link LayerBlock#MAX_LAYERS}
 * layers and stays in the grid, worn by one cut, until it wears out.
 */
public class SawItem extends Item {
	public SawItem(Properties properties) {
		super(properties);
	}

	@Override
	public ItemStackTemplate getCraftingRemainder(ItemStack stack) {
		ItemStack worn = stack.copyWithCount(1);
		worn.setDamageValue(worn.getDamageValue() + 1);
		return worn.getDamageValue() >= worn.getMaxDamage() ? null : ItemStackTemplate.fromNonEmptyStack(worn);
	}
}
