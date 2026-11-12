package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.remnants.Blight;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A wolf the blight has changed ({@link Blight#blighted}) is drawn tinted a sick green. */
@Mixin(LivingEntityRenderer.class)
abstract class BlightedWolfTintMixin {
	/** The sick green the changed wolf's model is tinted (ARGB). */
	private static final int SICK_GREEN = 0xFFB4D878;
	private static final RenderStateDataKey<Boolean> BLIGHTED = RenderStateDataKey.create(() -> "jaymoskingdoms:blighted_wolf");

	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymoskingdoms$blightExtract(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		state.setData(BLIGHTED, Blight.blighted(entity));
	}

	@Inject(method = "getModelTint(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)I", at = @At("HEAD"), cancellable = true)
	private void jaymoskingdoms$blightTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
		if (Boolean.TRUE.equals(state.getData(BLIGHTED))) {
			cir.setReturnValue(SICK_GREEN);
		}
	}
}
