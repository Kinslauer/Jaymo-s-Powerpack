package com.jaymospowers.mixin;

import com.jaymospowers.alchemy.oil.Oils;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A blade with oil on it shimmers like an enchanted one (the sheen, with the motes {@code OilClient} adds when it is held). */
@Mixin(ItemStack.class)
abstract class OilFoilMixin {
	@Inject(method = "hasFoil", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$oilSheen(CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && Oils.OILED != null && ((ItemStack) (Object) this).has(Oils.OILED)) {
			cir.setReturnValue(true);
		}
	}
}
