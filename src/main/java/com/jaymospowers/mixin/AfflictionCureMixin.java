package com.jaymospowers.mixin;

import com.jaymospowers.core.wounds.Afflictions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.20.26) Whatever a player finishes eating or drinking is shown to {@link Afflictions}: honey, milk and the Potion of
 * Cleansing cure, and water may go down the wrong pipe.
 */
@Mixin(ItemStack.class)
abstract class AfflictionCureMixin {
	@Inject(method = "finishUsingItem", at = @At("HEAD"))
	private void jaymospowers$cures(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
		if (entity instanceof ServerPlayer player) {
			Afflictions.consumed(player, ((ItemStack)(Object)this).copy());
		}
	}
}
