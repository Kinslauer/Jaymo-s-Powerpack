package com.jaymoskingdoms.mixin.locks;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the grid a result slot takes its ingredients from, for {@link TreeLockCraftMixin}. */
@Mixin(ResultSlot.class)
public interface ResultSlotAccessor {
	@Accessor("craftSlots")
	CraftingContainer jaymoskingdoms$craftSlots();
}
