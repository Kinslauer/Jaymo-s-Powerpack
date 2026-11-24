package com.jaymospowers.client.mixin;

import com.jaymospowers.client.body.BodyFaces;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** MCA's blink face is also its wince: right after a hit its villagers (and the players it draws as villagers) shut their eyes (see BodyFaces). */
@Pseudo
@Mixin(targets = "net.conczin.mca.client.render.VillagerVisuals", remap = false)
abstract class McaWinceBlinkMixin {
	@Shadow
	public abstract int entityId();

	@Inject(method = "isBlinking", at = @At("HEAD"), cancellable = true, remap = false)
	private void jaymospowers$wince(CallbackInfoReturnable<Boolean> cir) {
		if (BodyFaces.wincing(this.entityId())) {
			cir.setReturnValue(true);
		}
	}
}
