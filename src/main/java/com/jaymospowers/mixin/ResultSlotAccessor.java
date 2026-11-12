package com.jaymospowers.mixin;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The crafting grid behind a crafting result slot, for {@link ArmourWearSlotMixin}. */
@Mixin(ResultSlot.class)
public interface ResultSlotAccessor {
	@Accessor("craftSlots")
	CraftingContainer jaymospowers$craftSlots();
}
