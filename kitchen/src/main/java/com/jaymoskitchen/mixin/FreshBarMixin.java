package com.jaymoskitchen.mixin;

import com.jaymoskitchen.fresh.Freshness;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Food that rots shows how fresh it still is as an item bar, green to red ({@link Freshness}). Drawn client side only. */
@Mixin(ItemStack.class)
abstract class FreshBarMixin {
	@Inject(method = "isBarVisible", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$freshVisible(CallbackInfoReturnable<Boolean> cir) {
		if (Freshness.clientToday >= 0 && Freshness.left((ItemStack)(Object)this, Freshness.clientToday) >= 0.0F) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getBarWidth", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$freshWidth(CallbackInfoReturnable<Integer> cir) {
		float left = Freshness.clientToday < 0 ? -1.0F : Freshness.left((ItemStack)(Object)this, Freshness.clientToday);
		if (left >= 0.0F) {
			cir.setReturnValue(Math.round(13.0F * left));
		}
	}

	@Inject(method = "getBarColor", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$freshColor(CallbackInfoReturnable<Integer> cir) {
		float left = Freshness.clientToday < 0 ? -1.0F : Freshness.left((ItemStack)(Object)this, Freshness.clientToday);
		if (left >= 0.0F) {
			cir.setReturnValue(Mth.hsvToRgb(left / 3.0F, 0.9F, 1.0F));
		}
	}
}
