package com.jaymospowers.tinkering.mixin;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What a shapeless grid recipe makes, for {@code Uncraftable}: Tool Station builds drop the grid recipes of what they make. */
@Mixin(ShapelessRecipe.class)
public interface GridShapelessResultAccessor {
	@Accessor("result")
	ItemStackTemplate jaymospowers$result();
}
