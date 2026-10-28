package com.jaymospowers.client.mixin.tacz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (0.17.135) Andy: "there should be more muzzle flash on the guns from the future". TaCZ (GPL-3.0 code; optional,
 * nothing of it copied, its art untouched) draws a gun's muzzle flash at the size its gun display asks for; we draw it
 * {@link #SCALE} times that, on every gun, in first and third person. The burst others see around a shot is the
 * server's ({@code MuzzleFlash}). Skipped when TaCZ isn't there.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash", remap = false)
abstract class TaczMuzzleFlashMixin {
	/** How much bigger than TaCZ's own a muzzle flash is drawn. */
	private static final float SCALE = 2.2F;

	@Inject(method = "getScale", at = @At("RETURN"), cancellable = true, require = 0)
	private void jaymospowers$bigger(CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(cir.getReturnValueF() * SCALE);
	}
}
