package com.jaymospowers.client.mixin.shouldersurfing;

import com.github.exopandora.shouldersurfing.api.client.Perspective;
import com.github.exopandora.shouldersurfing.api.config.IPerspectiveConfig;
import com.jaymospowers.client.FrontCamera;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Shoulder Surfing Reloaded (MIT; optional, nothing of it copied) takes F5 before vanilla does and goes first person,
 * over the shoulder, then the front. Its F5 skips the front (the view after it is taken instead), which has its own key
 * now ({@link FrontCamera}). Its setting for the front stays on, so the front key still reaches it. Skipped when
 * Shoulder Surfing isn't there.
 */
@Pseudo
@Mixin(targets = "com.github.exopandora.shouldersurfing.client.ShoulderSurfing", remap = false)
abstract class ShoulderSurfingFrontMixin {
	@WrapOperation(method = "togglePerspective", require = 0, at = @At(value = "INVOKE",
		target = "Lcom/github/exopandora/shouldersurfing/api/client/Perspective;next(Lcom/github/exopandora/shouldersurfing/api/config/IPerspectiveConfig;)Lcom/github/exopandora/shouldersurfing/api/client/Perspective;"))
	private Perspective jaymospowers$skipFront(Perspective current, IPerspectiveConfig config, Operation<Perspective> original) {
		Perspective next = original.call(current, config);
		return next == Perspective.THIRD_PERSON_FRONT ? original.call(next, config) : next;
	}
}
