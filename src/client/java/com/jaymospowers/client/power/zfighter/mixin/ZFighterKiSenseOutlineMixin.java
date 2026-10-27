package com.jaymospowers.client.power.zfighter.mixin;

import com.jaymospowers.client.power.zfighter.ZFighterKiSenseClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.17.45, Andy's NOEA picks) Ki sense's outline colour: dim when weaker than you, pale when even, bright red when stronger. */
@Mixin(EntityRenderer.class)
abstract class ZFighterKiSenseOutlineMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$kiSense(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		int color = ZFighterKiSenseClient.color(entity);
		if (color != 0) {
			state.outlineColor = 0xFF000000 | color;
		}
	}
}
