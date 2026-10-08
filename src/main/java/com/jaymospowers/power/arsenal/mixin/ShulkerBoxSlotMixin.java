package com.jaymospowers.power.arsenal.mixin;

import com.jaymospowers.power.arsenal.Summoned;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No hiding summoned weapons in a shulker box either (its slot overrides {@code mayPlace}). */
@Mixin(ShulkerBoxSlot.class)
public abstract class ShulkerBoxSlotMixin {
	@Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$arsenalStayHeld(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (Summoned.is(stack)) {
			cir.setReturnValue(false);
		}
	}
}
