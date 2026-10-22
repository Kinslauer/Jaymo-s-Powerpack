package com.jaymoskingdoms.client.mixin.iris;

import com.jaymoskingdoms.client.DarkNights;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.164) Gives every shader program the moonlight uniform ({@link DarkNights.ShaderSkyLight}), next to Iris's own
 * worldTime, moonPhase and the rest. Skipped when Iris isn't there.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
abstract class IrisUniformsMixin {
	@Inject(method = "addNonDynamicUniforms", at = @At("TAIL"), require = 0)
	private static void jaymoskingdoms$moonlight(@Coerce Object uniforms, @Coerce Object idMap, @Coerce Object directives, @Coerce Object updateNotifier, CallbackInfo ci) {
		DarkNights.ShaderSkyLight.register(uniforms);
	}
}
