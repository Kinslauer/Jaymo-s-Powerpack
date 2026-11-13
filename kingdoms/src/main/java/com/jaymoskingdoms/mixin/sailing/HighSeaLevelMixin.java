package com.jaymoskingdoms.mixin.sailing;

import com.jaymoskingdoms.seas.HighSea;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Overworld's sea stands higher in a world made with the higher sea ({@link HighSea}). */
@Mixin(NoiseGeneratorSettings.class)
public class HighSeaLevelMixin {
	@Inject(method = "seaLevel", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$higherSea(CallbackInfoReturnable<Integer> cir) {
		int level = HighSea.seaLevel((NoiseGeneratorSettings)(Object)this, cir.getReturnValueI());
		if (level != cir.getReturnValueI()) {
			cir.setReturnValue(level);
		}
	}
}
