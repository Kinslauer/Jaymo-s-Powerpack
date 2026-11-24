package com.jaymospowers.client.mixin.carryon;

import com.jaymospowers.client.power.web.FirstPersonCompat;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Andy: "when you pick blocks up you should be able to see the block that you picked up, from first and third person".
 * Carry On (LGPL-3.0, Tschipp; optional, shipped unchanged, nothing of it copied) draws nothing at all, in either view,
 * when First-person Model ({@code firstperson}) is loaded, which we ship. Its draw goes ahead here, except in your own
 * first-person hands while First-person Model is drawing your body (the body's layer already holds the block then, so
 * it isn't drawn twice). Skipped when Carry On isn't there.
 */
@Pseudo
@Mixin(targets = "tschipp.carryon.client.render.CarriedObjectRender", remap = false)
abstract class CarryOnDrawMixin {
	@ModifyExpressionValue(method = "draw", at = @At(value = "INVOKE",
		target = "Ltschipp/carryon/platform/services/IPlatformHelper;isModLoaded(Ljava/lang/String;)Z"), require = 0)
	private static boolean jaymospowers$drawAnyway(boolean loaded, @Local(argsOnly = true) boolean firstPersonHands) {
		return loaded && firstPersonHands && FirstPersonCompat.bodyShown();
	}
}
