package com.jaymoskitchen.horse;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Saddlebags: put them in a horse's armor slot and the horse gets 15 slots of storage, like a donkey with a chest.
 * The goods are kept in the bags themselves, so taking them off (or the horse dying) doesn't spill them.
 */
public class SaddlebagItem extends Item {
	public SaddlebagItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public void onDestroyed(ItemEntity entity) {
		// burnt or blown up on the ground: the goods fall out
		ItemContainerContents contents = entity.getItem().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		entity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		ItemUtils.onContainerDestroyed(entity, contents.nonEmptyItemCopyStream());
	}
}
