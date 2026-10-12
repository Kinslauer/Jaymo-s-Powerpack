package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Professions;
import java.util.Map;
import net.conczin.mca.resources.SkinSelection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.5.13) A settlement's workers dress for the work: MCA's clothes for the nearest trade (a builder a mason's, a miner a miner's). */
@Mixin(SkinSelection.class)
abstract class McaSkinMixin {
	@Inject(method = "mapProfession", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$clothes(String profession, Map<String, String> conversions, CallbackInfoReturnable<String> cir) {
		String clothes = Professions.clothes(profession);
		if (clothes != null) {
			cir.setReturnValue(clothes);
		}
	}
}
