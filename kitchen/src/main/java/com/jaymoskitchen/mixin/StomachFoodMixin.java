package com.jaymoskitchen.mixin;

import com.jaymoskitchen.stomach.Stomach;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hearty meals ({@link Stomach}): what you eat goes into a stomach slot (at the tail, so food a power eats instead never counts). */
@Mixin(FoodProperties.class)
abstract class StomachFoodMixin {
	@Inject(method = "onConsume", at = @At("TAIL"))
	private void jaymoskitchen$intoStomach(Level level, LivingEntity user, ItemStack stack, Consumable consumable, CallbackInfo ci) {
		if (user instanceof ServerPlayer player) {
			Stomach.eat(player, stack, consumable.animation() == ItemUseAnimation.DRINK);
		}
	}
}
