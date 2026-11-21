package com.jaymospowers.client.bits.mixin;

import com.jaymospowers.client.bits.SoulFlames;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Soul fire burns blue: a burning entity whose fire is the soul fire's has its flames drawn from the soul fire's two frames ({@link SoulFlames}). */
@Mixin(FlameFeatureRenderer.class)
abstract class SoulFlameFeatureRendererMixin {
	@WrapOperation(method = "buildGroup", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/feature/FlameFeatureRenderer;prepare(Lnet/minecraft/client/renderer/feature/FlameFeatureRenderer$Submit;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
	private void jaymospowers$soulFlames(FlameFeatureRenderer self, FlameFeatureRenderer.Submit submit, VertexConsumer consumer,
		TextureAtlasSprite first, TextureAtlasSprite second, Operation<Void> original, @Local(argsOnly = true) FeatureFrameContext context) {
		if (SoulFlames.blue(submit.entityRenderState())) {
			first = context.atlasManager().get(SoulFlames.SOUL_0);
			second = context.atlasManager().get(SoulFlames.SOUL_1);
		}
		original.call(self, submit, consumer, first, second);
	}
}
