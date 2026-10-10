package com.jaymospowers.mixin;

import com.jaymospowers.core.Carry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A carried player is out of the way of crosshairs and projectiles too (Player overrides isPickable). */
@Mixin(Player.class)
abstract class CarryPlayerMixin {
	@Inject(method = "isPickable", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$carriedNotPickable(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && Carry.isCarried((Entity)(Object)this)) {
			cir.setReturnValue(false);
		}
	}
}
