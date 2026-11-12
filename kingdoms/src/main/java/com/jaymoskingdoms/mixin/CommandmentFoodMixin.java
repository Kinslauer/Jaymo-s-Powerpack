package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Commandments;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.7.535) What a player eats may break a commandment: rot, or raw meat ({@link Commandments#ate}). */
@Mixin(FoodProperties.class)
abstract class CommandmentFoodMixin {
	@Inject(method = "onConsume", at = @At("HEAD"))
	private void jaymoskingdoms$commandments(Level level, LivingEntity eater, ItemStack stack, Consumable consumable, CallbackInfo ci) {
		if (!level.isClientSide()) {
			Commandments.ate(eater, stack);
			com.jaymoskingdoms.hunger.HungerCurse.ateStart(eater, stack);
			com.jaymoskingdoms.thirst.Thirst.ateStart(eater);
		}
	}

	/** (the curse of the hungry) ordinary food feeds the cursed by half. */
	@Inject(method = "onConsume", at = @At("TAIL"))
	private void jaymoskingdoms$poorFeeding(Level level, LivingEntity eater, ItemStack stack, Consumable consumable, CallbackInfo ci) {
		if (!level.isClientSide()) {
			com.jaymoskingdoms.hunger.HungerCurse.ateDone(eater);
			com.jaymoskingdoms.thirst.Thirst.ateDone(eater);
		}
	}
}
