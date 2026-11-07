package com.jaymospowers.freerun.moves.mixin;

import com.jaymospowers.freerun.moves.Hiding;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hiding in leaves or hay ({@link Hiding}): mobs barely see you. Targeting scales a mob's follow range by this, and
 * never below two blocks, so only one right beside you finds you.
 */
@Mixin(LivingEntity.class)
abstract class HidingVisibilityMixin {
	@Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$hidden(ServerLevel level, Entity targeting, CallbackInfoReturnable<Double> cir) {
		if ((Object)this instanceof Player player && Hiding.hidden(player)) {
			cir.setReturnValue(0.0);
		}
	}
}
