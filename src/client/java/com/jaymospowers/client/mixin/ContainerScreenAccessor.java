package com.jaymospowers.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen sits, to put the hide-armour buttons beside its armour slots. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
	@Accessor("leftPos")
	int jaymospowers$leftPos();

	@Accessor("topPos")
	int jaymospowers$topPos();
}
