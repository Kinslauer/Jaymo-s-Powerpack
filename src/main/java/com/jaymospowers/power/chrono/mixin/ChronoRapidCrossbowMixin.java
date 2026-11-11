package com.jaymospowers.power.chrono.mixin;

import com.jaymospowers.power.chrono.ChronoRapid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A speedster's crossbow loads at once: its charge takes a tick. */
@Mixin(CrossbowItem.class)
abstract class ChronoRapidCrossbowMixin {
	@Inject(method = "getChargeDuration", at = @At("RETURN"), cancellable = true)
	private static void jaymospowers$rapidLoad(ItemStack stack, LivingEntity entity, CallbackInfoReturnable<Integer> cir) {
		if (ChronoRapid.rapid(entity)) {
			cir.setReturnValue(1);
		}
	}
}
