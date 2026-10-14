package com.jaymoskingdoms.client.mixin;

import com.jaymoskingdoms.wild.SpiderKinds;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.spider.Spider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A spider's kind ({@link SpiderKinds}) goes on its render state, for SpiderSkinMixin. */
@Mixin(LivingEntityRenderer.class)
abstract class SpiderKindStateMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$spiderKind(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		SpiderKinds.Kind kind = entity instanceof Spider ? SpiderKinds.of(entity) : SpiderKinds.Kind.PLAIN;
		state.setData(com.jaymoskingdoms.client.SpiderSkins.KIND, kind == SpiderKinds.Kind.PLAIN ? null : kind);
	}
}
