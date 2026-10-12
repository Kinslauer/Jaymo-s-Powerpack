package com.jaymoskingdoms.mixin.mca;

import net.conczin.mca.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.5.41) No dyed hair at birth: MCA's 2% chance of a pink, blue or green head is off, whatever the config file says. */
@Mixin(Config.class)
abstract class McaConfigMixin {
	@Inject(method = "getInstance", at = @At("RETURN"))
	private static void jaymoskingdoms$naturalHair(CallbackInfoReturnable<Config> cir) {
		if (cir.getReturnValue() != null) {
			cir.getReturnValue().coloredHairChance = 0.0F;
		}
	}
}
