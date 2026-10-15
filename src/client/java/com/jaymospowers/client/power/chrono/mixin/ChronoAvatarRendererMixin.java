package com.jaymospowers.client.power.chrono.mixin;

import com.jaymospowers.client.power.chrono.ChronoFlowPose;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts a Time Flow blow, Overclock's run and its streak mark on a player's render state (ChronoFlowPose, ChronoRunPose, ChronoStreak). */
@Mixin(AvatarRenderer.class)
abstract class ChronoAvatarRendererMixin {
	@Inject(
		method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void jaymospowers$chronoFlow(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		ChronoFlowPose.extract(entity, state, partialTicks);
		com.jaymospowers.client.power.chrono.ChronoRunPose.extract(entity, state, partialTicks);
		com.jaymospowers.client.power.chrono.ChronoStreak.extract(entity, state);
	}
}
