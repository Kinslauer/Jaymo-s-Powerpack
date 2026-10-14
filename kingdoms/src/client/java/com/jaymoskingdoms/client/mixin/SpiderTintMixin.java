package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.wild.SpiderKinds;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.spider.Spider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A spider's skin takes its kind's tint ({@link SpiderKinds}); its glowing eyes stay red. */
@Mixin(LivingEntityRenderer.class)
abstract class SpiderTintMixin {
	@Unique
	private static final RenderStateDataKey<Integer> TINT = RenderStateDataKey.create(() -> "jaymoskingdoms:spider_tint");

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$spiderKind(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		int tint = entity instanceof Spider ? SpiderKinds.of(entity).tint : -1;
		state.setData(TINT, tint == -1 ? null : tint);
	}

	@Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
	private void jaymoskingdoms$spiderTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
		Integer tint = state.getData(TINT);
		if (tint != null) {
			cir.setReturnValue(ARGB.multiply(cir.getReturnValueI(), 0xFF000000 | tint));
		}
	}
}
