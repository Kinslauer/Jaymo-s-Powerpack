package com.jaymoskitchen.mixin;

import com.jaymoskitchen.farm.RawMilk;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Raw milk ({@link RawMilk}) may sicken whoever drinks it: once milk has done its clearing, before the stack is used up. */
@Mixin(Consumable.class)
abstract class RawMilkConsumableMixin {
	@Inject(method = "onConsume", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V"))
	private void jaymoskitchen$rawMilk(Level level, LivingEntity user, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (user instanceof ServerPlayer player) {
			RawMilk.drunk(player, stack);
		}
	}
}
