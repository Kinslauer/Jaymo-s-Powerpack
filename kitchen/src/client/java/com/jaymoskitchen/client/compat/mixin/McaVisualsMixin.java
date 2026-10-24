package com.jaymoskitchen.client.compat.mixin;

import com.jaymoskitchen.client.corpse.ClosedEyes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (0.5.22) MCA's blink, held shut while it draws a corpse's face ({@code McaFaceLayerMixin}). Render thread only. */
@Pseudo
@Mixin(targets = "net.conczin.mca.client.render.VillagerVisuals", remap = false)
abstract class McaVisualsMixin {
	@Inject(method = "isBlinking()Z", at = @At("HEAD"), cancellable = true)
	private void jaymoskitchen$shut(CallbackInfoReturnable<Boolean> cir) {
		if (ClosedEyes.drawingShut) {
			cir.setReturnValue(true);
		}
	}
}
