package com.jaymospowers.client.power.xenoform.mixin;

import com.jaymospowers.client.power.xenoform.HowlNoseClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Howl's nose: the tracked prey and the bleeding are outlined on its own screen (HowlNoseClient). */
@Mixin(EntityRenderer.class)
abstract class HowlNoseOutlineMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$howlNose(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		int color = HowlNoseClient.outline(entity);
		if (color != 0) {
			state.outlineColor = color;
		}
	}
}
