package com.jaymoskingdoms.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen sits, to put the wardrobe button on the inventory's player box. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerPosAccessor {
	@Accessor("leftPos")
	int jaymoskingdoms$leftPos();

	@Accessor("topPos")
	int jaymoskingdoms$topPos();
}
