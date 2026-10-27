package com.jaymospowers.client.power.zfighter.mixin;

import com.jaymospowers.client.LockOnClient;
import com.jaymospowers.client.power.zfighter.ZFighterFlightClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A Z Fighter's scroll while holding Z is his flight (take off, poses, land), not the hotbar's. (0.17.45) Anyone's scroll
 * while locked on moves the lock left or right ({@link LockOnClient#scroll}).
 */
@Mixin(MouseHandler.class)
abstract class ZFighterScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void jaymospowers$zfighterScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (ZFighterFlightClient.scroll(yOffset) || LockOnClient.scroll(yOffset)) {
			ci.cancel();
		}
	}
}
