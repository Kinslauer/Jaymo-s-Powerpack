package com.jaymospowers.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.GrindstoneMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The grindstone's two input slots and its result, for {@link ArmourWearSlotMixin}. */
@Mixin(GrindstoneMenu.class)
public interface GrindstoneMenuAccessor {
	@Accessor("repairSlots")
	Container jaymospowers$repairSlots();

	@Accessor("resultSlots")
	Container jaymospowers$resultSlots();
}
