package com.jaymoskitchen.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets a kitchen tool be its own crafting remainder (it stays in the grid, as a bucket of milk leaves its bucket):
 * the item has to exist before it can be named as its own remainder.
 */
@Mixin(Item.class)
public interface ItemAccessor {
	@Mutable
	@Accessor("craftingRemainingItem")
	void jaymoskitchen$setCraftingRemainder(ItemStackTemplate remainder);
}
