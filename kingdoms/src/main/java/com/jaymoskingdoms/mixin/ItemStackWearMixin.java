package com.jaymoskingdoms.mixin;

import com.jaymoskingdoms.Blessings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.509) The Anvil-Father's Sound Steel and Father's Fire: a point of wear on what is in a blessed player's hands is spared
 * ({@link Blessings#wear}). Powers' graded and tinker tool mixins call the real method, so their tools are covered too.
 */
@Mixin(ItemStack.class)
abstract class ItemStackWearMixin {
	@Inject(method = "processDurabilityChange", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$spareWear(int amount, ServerLevel level, @Nullable ServerPlayer player, CallbackInfoReturnable<Integer> cir) {
		if (player != null) {
			cir.setReturnValue(Blessings.wear((ItemStack)(Object)this, player, cir.getReturnValue()));
			// (0.7.535) a tool that breaks in the hand breaks the Anvil-Father's second
			com.jaymoskingdoms.Commandments.wore((ItemStack)(Object)this, player, cir.getReturnValue());
		}
	}
}
