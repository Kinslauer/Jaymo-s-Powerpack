package com.jaymospowers.tinkering.mixin;

import com.jaymospowers.tinkering.kiln.Kiln;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Raw ore and ore blocks (item tag {@code jaymospowers:slow_smelt}) take {@link Kiln#FURNACE_ORE_SLOWDOWN} times as long in
 * a plain furnace, so the smeltery and the blast furnace are worth building. Blast furnaces, smokers and Kitchen's
 * cooking are untouched.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
abstract class SlowOreSmeltMixin {
	@Inject(method = "getTotalCookTime(Lnet/minecraft/world/item/crafting/RecipeHolder;Lnet/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity;)I",
		at = @At("RETURN"), cancellable = true)
	private static void jaymospowers$slowOre(RecipeHolder<?> recipe, AbstractFurnaceBlockEntity furnace, CallbackInfoReturnable<Integer> cir) {
		if (furnace.getType() == BlockEntityTypes.FURNACE && furnace.getItem(0).is(Kiln.SLOW_SMELT)) {
			cir.setReturnValue(cir.getReturnValueI() * Kiln.FURNACE_ORE_SLOWDOWN);
		}
	}
}
