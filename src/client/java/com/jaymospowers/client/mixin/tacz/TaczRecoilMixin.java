package com.jaymospowers.client.mixin.tacz;

import com.jaymospowers.client.armory.GunHandling;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * (ad_recoil) Recoil by gun kind. TaCZ (GPL-3.0, optional, nothing of it copied) kicks the view along two curves per shot,
 * up (the first one read) and sideways (the second); {@link GunHandling} scales each by the gun's kind and holds it at its
 * highest, so the view stays where the kick left it.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.event.CameraSetupEvent", remap = false)
abstract class TaczRecoilMixin {
	@ModifyExpressionValue(method = "applyCameraRecoil", require = 0, at = @At(value = "INVOKE", ordinal = 0,
		target = "Lorg/apache/commons/math3/analysis/polynomials/PolynomialSplineFunction;value(D)D"))
	private static double jaymospowers$pitch(double value) {
		return GunHandling.recoilPitch(value);
	}

	@ModifyExpressionValue(method = "applyCameraRecoil", require = 0, at = @At(value = "INVOKE", ordinal = 1,
		target = "Lorg/apache/commons/math3/analysis/polynomials/PolynomialSplineFunction;value(D)D"))
	private static double jaymospowers$yaw(double value) {
		return GunHandling.recoilYaw(value);
	}
}
