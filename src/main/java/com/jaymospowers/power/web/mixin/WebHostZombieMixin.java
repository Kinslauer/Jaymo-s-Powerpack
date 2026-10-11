package com.jaymospowers.power.web.mixin;

import com.jaymospowers.power.web.WebBehemoth;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A symbiote behemoth: the big body keeps the sun off its dead flesh, and it never turns into a drowned. */
@Mixin(Zombie.class)
abstract class WebHostZombieMixin {
	@Inject(method = "isSunSensitive", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$hostSun(CallbackInfoReturnable<Boolean> cir) {
		if (WebBehemoth.isHost((Zombie)(Object)this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "convertsInWater", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$hostWater(CallbackInfoReturnable<Boolean> cir) {
		if (WebBehemoth.isHost((Zombie)(Object)this)) {
			cir.setReturnValue(false);
		}
	}
}
