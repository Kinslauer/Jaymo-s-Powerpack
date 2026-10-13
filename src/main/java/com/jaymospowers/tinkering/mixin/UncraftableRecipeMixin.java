package com.jaymospowers.tinkering.mixin;

import com.jaymospowers.tinkering.Uncraftable;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Other mods' recipes we've taken out ({@link Uncraftable}) never reach the recipe map. */
@Mixin(RecipeMap.class)
abstract class UncraftableRecipeMixin {
	@Redirect(method = "create", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/HolderLookup;listElements()Ljava/util/stream/Stream;"))
	private static Stream<Holder.Reference<Recipe<?>>> jaymospowers$dropUncraftable(HolderLookup<Recipe<?>> recipes) {
		return recipes.listElements().filter(Uncraftable::keeps);
	}
}
