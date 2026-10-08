package com.jaymospowers.power.web.mixin;

import com.jaymospowers.power.web.WebSymbiote;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Raw meat eaten by a bonded web slinger feeds the symbiote instead of them (see WebSymbiote). */
@Mixin(FoodProperties.class)
abstract class WebFoodPropertiesMixin {
	@Inject(method = "onConsume", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$webSymbioteEats(Level level, LivingEntity user, ItemStack stack, Consumable consumable, CallbackInfo ci) {
		if (user instanceof ServerPlayer player && WebSymbiote.feedOnMeat(player, stack, ((FoodProperties)(Object)this).nutrition())) {
			ci.cancel();
		}
	}
}
