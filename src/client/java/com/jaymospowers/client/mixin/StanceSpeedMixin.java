package com.jaymospowers.client.mixin;

import com.jaymospowers.core.Stance;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.18.57) The low crouch walks slower than a sneak ({@link Stance#LOW_SPEED}). */
@Mixin(LocalPlayer.class)
abstract class StanceSpeedMixin {
	@Inject(method = "modifyInput", at = @At("RETURN"), cancellable = true)
	private void jaymospowers$lowCrouchSpeed(Vec2 input, CallbackInfoReturnable<Vec2> cir) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (self.isCrouching() && Stance.low(self)) {
			cir.setReturnValue(cir.getReturnValue().scale(Stance.LOW_SPEED));
		}
	}
}
