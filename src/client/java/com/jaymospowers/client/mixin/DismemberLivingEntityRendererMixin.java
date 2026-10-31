package com.jaymospowers.client.mixin;

import com.jaymospowers.client.Dismembered;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Which parts a body has lost, onto its render state (see Dismembered), and what's dressed or splinted (DressingsLayer), a Flame Avatar's fire. */
@Mixin(LivingEntityRenderer.class)
abstract class DismemberLivingEntityRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$lostParts(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		Dismembered.extract(entity, state);
		// (0.15.90) and the bandages and splints that show
		com.jaymospowers.client.wounds.DressingsLayer.extract(entity, state);
		// (0.18.117) weapons stuck in the body
		com.jaymospowers.client.armory.LodgedLayer.extract(entity, state);
		// (0.18.82) and a wooden or iron limb standing in for a lost one
		com.jaymospowers.client.wounds.ProstheticLayer.extract(entity, state);
		// (0.15.151) and a Flame Avatar's burning body
		com.jaymospowers.client.power.ember.FlameAvatarClient.extract(entity, state);
	}
}
