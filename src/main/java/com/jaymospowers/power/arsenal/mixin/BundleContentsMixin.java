package com.jaymospowers.power.arsenal.mixin;

import com.jaymospowers.power.arsenal.Summoned;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ...or in a bundle, where the expiry scan couldn't reach them. */
@Mixin(BundleContents.class)
public abstract class BundleContentsMixin {
	@Inject(method = "canItemBeInBundle", at = @At("HEAD"), cancellable = true)
	private static void jaymospowers$arsenalNoBundle(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (Summoned.is(stack)) {
			cir.setReturnValue(false);
		}
	}
}
