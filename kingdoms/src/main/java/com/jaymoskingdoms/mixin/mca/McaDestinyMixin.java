package com.jaymoskingdoms.mixin.mca;

import com.jaymoskingdoms.Verdants;
import net.conczin.mca.CommonConfig;
import net.conczin.mca.server.DestinyLocationResolver;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.7.27) Andy: MCA's start-up destiny screen offers a village in every dimension; the Verdant World's are taken off
 * it. That world is reached only through the Z Fighter's Lookout door ({@link Verdants}).
 */
@Mixin(DestinyLocationResolver.class)
abstract class McaDestinyMixin {
	@Inject(method = "isDimensionAllowed", at = @At("HEAD"), cancellable = true)
	private static void jaymoskingdoms$noVerdantWorld(ServerLevel level, CommonConfig config, CallbackInfoReturnable<Boolean> cir) {
		if (level.dimension().identifier().equals(Verdants.WORLD)) {
			cir.setReturnValue(false);
		}
	}
}
