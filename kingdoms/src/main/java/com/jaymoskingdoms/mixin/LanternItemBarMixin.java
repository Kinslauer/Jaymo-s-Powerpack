package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Lanterns;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.7.541) A lantern item that has burnt some of its fuel shows a durability-style bar for what is left ({@link Lanterns}). */
@Mixin(Item.class)
abstract class LanternItemBarMixin {
	@Inject(method = "isBarVisible", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$lanternBar(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack.is(Items.LANTERN) && Lanterns.barVisible(stack)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "getBarWidth", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$lanternBarWidth(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (stack.is(Items.LANTERN) && Lanterns.barVisible(stack)) {
			cir.setReturnValue(Lanterns.barWidth(stack));
		}
	}

	@Inject(method = "getBarColor", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$lanternBarColor(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (stack.is(Items.LANTERN) && Lanterns.barVisible(stack)) {
			cir.setReturnValue(Lanterns.barColor(stack));
		}
	}
}
