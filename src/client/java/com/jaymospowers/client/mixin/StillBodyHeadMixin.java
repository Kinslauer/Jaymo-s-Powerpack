package com.jaymospowers.client.mixin;

import com.jaymospowers.core.Meditate;
import com.jaymospowers.core.StillBody;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (Andy, 2026-11-20) Prone, sat or crouched, the body stays put (StillBody): the head turns after the camera only so far. */
@Mixin(LivingEntityRenderer.class)
abstract class StillBodyHeadMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$stillHead(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		// (on a seat MeditatePose turns the body to the seat and holds the head to it itself)
		if (entity instanceof Player player && StillBody.held(player) && !(Meditate.of(player) instanceof Meditate.State sat && sat.seated())) {
			state.yRot = StillBody.head(state.yRot);
		}
	}
}
