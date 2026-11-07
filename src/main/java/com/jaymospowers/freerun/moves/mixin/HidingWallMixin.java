package com.jaymospowers.freerun.moves.mixin;

import com.jaymospowers.freerun.moves.Hiding;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hiding in a hay bale ({@link Hiding}): the head is inside a solid block on purpose, so it doesn't suffocate you. */
@Mixin(LivingEntity.class)
abstract class HidingWallMixin {
	@Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$hidingBreathes(CallbackInfoReturnable<Boolean> cir) {
		if ((Object)this instanceof Player player && Hiding.hidden(player)) {
			cir.setReturnValue(false);
		}
	}
}
