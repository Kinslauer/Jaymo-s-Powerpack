package com.jaymoskingdoms.mixin.nightgifts;

import com.jaymoskingdoms.VampireTells;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (0.7.332) Normal food gives a vampire nothing ({@link VampireTells#noNutrition}): the eating (its sound, its animation,
 * its potion effects) goes on, but the nutrition and saturation don't reach them. A dhampir eats as anyone does.
 */
@Mixin(FoodProperties.class)
abstract class VampireFoodMixin {
	@WrapOperation(method = "onConsume", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
	private void jaymoskingdoms$ash(FoodData data, FoodProperties food, Operation<Void> original, @Local(argsOnly = true) LivingEntity eater) {
		if (!VampireTells.noNutrition(eater)) {
			original.call(data, food);
		}
	}
}
