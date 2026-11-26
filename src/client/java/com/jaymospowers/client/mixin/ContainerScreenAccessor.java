package com.jaymospowers.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen sits and how wide it is, to put the hide-armour buttons beside its armour slots and the deconstruct pick at its right edge. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
	@Accessor("leftPos")
	int jaymospowers$leftPos();

	@Accessor("topPos")
	int jaymospowers$topPos();

	@Accessor("imageWidth")
	int jaymospowers$imageWidth();
}
