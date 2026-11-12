package com.jaymoskingdoms.client.mixin.xaero;

import com.jaymoskingdoms.client.CastOutMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Cast Out road's empty map ({@link CastOutMap}): Xaero's Minimap draws nothing while it holds. Skipped without Xaero. */
@Pseudo
@Mixin(targets = "xaero.hud.render.HudRenderer", remap = false)
abstract class XaeroHudEmptyMixin {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
	private void jaymoskingdoms$emptyMap(CallbackInfo ci) {
		if (CastOutMap.empty()) {
			ci.cancel();
		}
	}
}
