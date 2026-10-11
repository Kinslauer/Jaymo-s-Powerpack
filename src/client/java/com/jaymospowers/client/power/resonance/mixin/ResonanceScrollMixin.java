package com.jaymospowers.client.power.resonance.mixin;

import com.jaymospowers.client.power.resonance.ResonanceInputClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A Resonance user's scroll while holding X (Tune) or Z (Throw Voice) is theirs, not the hotbar's. */
@Mixin(MouseHandler.class)
abstract class ResonanceScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$resonanceScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (ResonanceInputClient.scroll(yOffset)) {
			ci.cancel();
		}
	}
}
