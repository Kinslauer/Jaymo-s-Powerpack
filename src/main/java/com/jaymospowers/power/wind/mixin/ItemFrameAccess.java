package com.jaymospowers.power.wind.mixin;

import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Soras' Stone's frame is fixed, so breaking the blackstone behind it never drops the blade. */
@Mixin(ItemFrame.class)
public interface ItemFrameAccess {
	@Accessor("fixed")
	void jaymospowers$setFixed(boolean fixed);
}
