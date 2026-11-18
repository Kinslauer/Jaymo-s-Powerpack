package com.jaymospowers.client.bits.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The slot the mouse is over in a container screen, for sharing its item in chat. */
@Mixin(AbstractContainerScreen.class)
public interface HoveredSlotAccessor {
	@Accessor("hoveredSlot")
	Slot jaymospowers$hoveredSlot();
}
