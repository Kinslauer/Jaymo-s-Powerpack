package com.jaymoskingdoms.client.mixin.equestrian;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.5.34) A container screen's size, to place the horse's stats in it. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("imageWidth")
	int jaymoskingdoms$imageWidth();

	@Accessor("imageHeight")
	int jaymoskingdoms$imageHeight();

	/** Where the screen's panel is, to hang the harness tab on the inventory's edge. */
	@Accessor("leftPos")
	int jaymoskingdoms$leftPos();

	@Accessor("topPos")
	int jaymoskingdoms$topPos();
}
