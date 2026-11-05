package com.jaymospowers.client.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (0.20.41) An item's drawn layers, for the blood laid over a held weapon (BloodItemOverlay). */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
	@Accessor("layers")
	ItemStackRenderState.LayerRenderState[] jaymospowers$layers();

	@Accessor("activeLayerCount")
	int jaymospowers$activeLayerCount();
}
