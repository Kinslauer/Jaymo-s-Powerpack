package com.jaymoskitchen.mixin;

import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reaches the crafting grid (see {@code CraftingMenuMixin}). */
@Mixin(AbstractCraftingMenu.class)
public interface AbstractCraftingMenuAccessor {
	@Accessor("craftSlots")
	CraftingContainer jaymoskitchen$craftSlots();
}
