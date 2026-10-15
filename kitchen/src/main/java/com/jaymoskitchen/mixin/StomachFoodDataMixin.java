package com.jaymoskitchen.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Hearty meals ({@link com.jaymoskitchen.stomach.Stomach}): a full hunger bar no longer heals; your meals do. Starving still hurts. */
@Mixin(FoodData.class)
abstract class StomachFoodDataMixin {
	@ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0)
	private boolean jaymoskitchen$noHungerRegen(boolean naturalRegen) {
		return false;
	}
}
