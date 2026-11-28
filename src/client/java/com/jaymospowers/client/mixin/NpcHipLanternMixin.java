package com.jaymospowers.client.mixin;

import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An NPC's off-hand lantern hangs at its hip (WornLanterns.extractNpc), once the renderer has put its hands' items and
 * arm poses in the render state.
 */
@Mixin(HumanoidMobRenderer.class)
abstract class NpcHipLanternMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$hipLantern(Mob entity, HumanoidRenderState state, float partialTicks, CallbackInfo ci) {
		com.jaymospowers.client.baubles.WornLanterns.extractNpc(entity, state, partialTicks);
	}
}
