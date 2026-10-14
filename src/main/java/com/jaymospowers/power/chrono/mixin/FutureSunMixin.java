package com.jaymospowers.power.chrono.mixin;

import com.jaymospowers.power.chrono.future.Future;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Nothing burns under the future's ashen sun: its feral dead roam by day too. */
@Mixin(Mob.class)
abstract class FutureSunMixin {
	@Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$futureSun(CallbackInfoReturnable<Boolean> cir) {
		if (((Mob) (Object) this).level().dimension() == Future.FUTURE) {
			cir.setReturnValue(false);
		}
	}
}
