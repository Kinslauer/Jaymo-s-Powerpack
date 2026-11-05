package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.client.power.xenoform.SkitterCling;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.20.59) Skitter's ceiling cling, held before the player's own move ({@link SkitterCling}). */
@Mixin(LocalPlayer.class)
abstract class SkitterClingMixin {
	@Inject(method = "aiStep", at = @At("HEAD"))
	private void jaymospowers$skitterCling(CallbackInfo ci) {
		SkitterCling.beforeMove((LocalPlayer)(Object)this);
	}
}
