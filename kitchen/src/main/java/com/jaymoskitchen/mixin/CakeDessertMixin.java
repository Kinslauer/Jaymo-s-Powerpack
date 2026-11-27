package com.jaymoskitchen.mixin;

import net.minecraft.world.level.block.CakeBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Cake is a dessert (Granular Hunger's idea, CC0, by tetro48): a slice off a placed cake goes down even on a full bar. */
@Mixin(CakeBlock.class)
abstract class CakeDessertMixin {
	@ModifyArg(method = "eat", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;canEat(Z)Z"), index = 0)
	private static boolean jaymoskitchen$dessert(boolean ignoreHunger) {
		return true;
	}
}
