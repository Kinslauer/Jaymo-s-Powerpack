package com.jaymoskingdoms.mixin.fifth;

import com.jaymoskingdoms.FeatsFight;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A Crossbow Expert's crossbow loads in less of the time ({@link FeatsFight#loading}), and a Gunslinger's ({@link com.jaymoskingdoms.Paths#loading}). */
@Mixin(CrossbowItem.class)
abstract class CrossbowChargeMixin {
	@Inject(method = "getChargeDuration", at = @At("RETURN"), cancellable = true)
	private static void jaymoskingdoms$expert(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
		int ticks = FeatsFight.loading(user, cir.getReturnValueI());
		ticks = com.jaymoskingdoms.Paths.loading(user, ticks);
		if (ticks != cir.getReturnValueI()) {
			cir.setReturnValue(ticks);
		}
	}
}
