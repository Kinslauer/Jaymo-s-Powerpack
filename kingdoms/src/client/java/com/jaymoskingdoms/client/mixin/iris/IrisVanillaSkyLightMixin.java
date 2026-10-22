package com.jaymoskingdoms.client.mixin.iris;

import com.jaymoskingdoms.client.DarkNights;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.7.164) Dark nights under shader packs ({@link DarkNights.ShaderSkyLight}): the sky light Iris hands a pack's
 * entities, hands, particles and block entities is scaled by the moonlight. Skipped when Iris isn't there.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.transform.transformer.VanillaTransformer", remap = false)
abstract class IrisVanillaSkyLightMixin {
	@ModifyConstant(method = "transform", constant = @Constant(stringValue = "vec4(iris_UV2, 0.0, 1.0)"), require = 0)
	private static String jaymoskingdoms$skyLight(String light) {
		return DarkNights.ShaderSkyLight.scale(light, "vec2(iris_UV2)");
	}

	@ModifyConstant(method = "transform", constant = @Constant(stringValue = "vec4(iris_LightUV, 0.0, 1.0)"), require = 0)
	private static String jaymoskingdoms$skyLightUniform(String light) {
		return DarkNights.ShaderSkyLight.scale(light, "vec2(iris_LightUV)");
	}

	@Inject(method = "transform", at = @At("TAIL"), require = 0)
	private static void jaymoskingdoms$declare(@Coerce Object parser, @Coerce Object tree, @Coerce Object root, @Coerce Object parameters, CallbackInfo ci) {
		DarkNights.ShaderSkyLight.declare(parser, tree);
	}
}
