package com.jaymoskitchen.mixin;

import com.jaymoskitchen.climate.Climate;
import com.jaymoskitchen.climate.Thirst;
import com.jaymoskitchen.stomach.Stomach;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hearty meals ({@link Stomach}): food with room in your stomach can be eaten on a full hunger bar; plain drinks fill the drink slot. */
@Mixin(Consumable.class)
abstract class StomachConsumableMixin {
	@Inject(method = "canConsume", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$roomInStomach(LivingEntity user, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (user instanceof Player player && stack.has(DataComponents.FOOD) && Stomach.wouldTake(player, stack)) {
			cir.setReturnValue(true);
		}
	}

	/** (0.5.25) Anything eaten or drunk: the water in it ({@link Thirst}). */
	@Inject(method = "onConsume", at = @At("HEAD"))
	private void jaymoskitchen$thirst(Level level, LivingEntity user, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (user instanceof ServerPlayer player && player.level().getGameRules().get(Climate.THIRST)) {
			Thirst.consumed(player, stack);
		}
	}

	/** Potions and milk (no food in them): the stack is still whole here. Food goes through {@link StomachFoodMixin}. */
	@Inject(method = "onConsume", at = @At("HEAD"))
	private void jaymoskitchen$drink(Level level, LivingEntity user, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (user instanceof ServerPlayer player && !stack.has(DataComponents.FOOD) && ((Consumable) (Object) this).animation() == ItemUseAnimation.DRINK) {
			Stomach.eat(player, stack, true);
		}
	}
}
