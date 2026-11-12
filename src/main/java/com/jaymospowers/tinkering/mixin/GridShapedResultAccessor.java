package com.jaymospowers.tinkering.mixin;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What a shaped grid recipe makes, for {@code Uncraftable}: Tool Station builds drop the grid recipes of what they make. */
@Mixin(ShapedRecipe.class)
public interface GridShapedResultAccessor {
	@Accessor("result")
	ItemStackTemplate jaymospowers$result();
}
