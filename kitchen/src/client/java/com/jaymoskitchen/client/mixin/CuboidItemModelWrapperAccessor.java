package com.jaymoskitchen.client.mixin;

import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** (From Farmer's Delight, MIT.) Lets the skillet's flipping model take the display transforms off its pan model (it draws the pan itself). */
@Mixin(CuboidItemModelWrapper.class)
public interface CuboidItemModelWrapperAccessor {
	@Accessor("properties")
	ModelRenderProperties jaymoskitchen$getProperties();

	@Accessor("properties")
	@Mutable
	void jaymoskitchen$setProperties(ModelRenderProperties properties);
}
