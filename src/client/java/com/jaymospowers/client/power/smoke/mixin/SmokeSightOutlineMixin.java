package com.jaymospowers.client.power.smoke.mixin;

import com.jaymospowers.client.power.smoke.SmokeSightClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smoke Sight's outline colour: pale grey, near black for the hostile. */
@Mixin(EntityRenderer.class)
abstract class SmokeSightOutlineMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$smokeSight(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		int color = SmokeSightClient.color(entity);
		if (color != 0) {
			state.outlineColor = 0xFF000000 | color;
		}
	}
}
