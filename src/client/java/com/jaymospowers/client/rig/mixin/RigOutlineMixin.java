package com.jaymospowers.client.rig.mixin;

import com.jaymospowers.client.rig.RigScanClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (0.11.83) The Rig's glow colours: red hostiles, gold players, a hot pink lock. */
@Mixin(EntityRenderer.class)
abstract class RigOutlineMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$rigScan(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		int color = RigScanClient.color(entity);
		if (color != 0) {
			state.outlineColor = 0xFF000000 | color;
		}
	}
}
