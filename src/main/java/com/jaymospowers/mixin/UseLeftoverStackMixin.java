package com.jaymospowers.mixin;

import com.jaymospowers.core.UseLeftover;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tells the user what finishing this item left (the bowl, the bottle), for {@link UseLeftoverHandMixin}. */
@Mixin(ItemStack.class)
abstract class UseLeftoverStackMixin {
	@Inject(method = "finishUsingItem", at = @At("RETURN"))
	private void jaymospowers$leftover(Level level, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
		if (user instanceof UseLeftover leftover) {
			leftover.jaymospowers$useLeftover(cir.getReturnValue());
		}
	}
}
